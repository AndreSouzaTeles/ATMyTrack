package com.atmytrack.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.roundToLong

data class LibraryState(val projects: List<Project> = emptyList(), val selected: String = "", val busy: String? = "Abrindo biblioteca…", val message: String? = null,
    val background:String?=null,val waveform:List<Float> = emptyList(),val imports:List<ImportItem> = emptyList(),val editRequestedId:String?=null) {
    val current get() = projects.find { it.id == selected }
}

/** One application-owned session keeps CrossMusic and PAD alive when the activity is recreated. */
class PlayerSession(private val app:Application) {
    private val viewModelScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    @Suppress("UNCHECKED_CAST") private fun <T:Application> getApplication():T=app as T
    private val crossStore=CrossMusicStore(app.filesDir)
    private val crossSaved=MutableStateFlow<List<CrossEntry>>(emptyList())
    val crossPlan=crossSaved.asStateFlow()
    private val crossMutable=MutableStateFlow(CrossMusicState())
    val crossMusic=crossMutable.asStateFlow()
    private val crossSaves=Channel<List<CrossEntry>>(Channel.CONFLATED)
    private var runEntries=emptyList<CrossEntry>()
    private var crossStartJob:Job?=null
    private var autoPlayRequested=false
    private var crossPadOwned=false
    private val engine = (app as TrackApplication).engine
    val pad = (app as TrackApplication).pad
    val rhythm = (app as TrackApplication).rhythm
    val playback = engine.state
    private val store = ProjectStore(app.filesDir)
    private val prefs = app.getSharedPreferences("settings", 0)
    private val mutable = MutableStateFlow(LibraryState())
    val library = mutable.asStateFlow()
    private val saves = Channel<List<Project>>(Channel.CONFLATED)
    private val tapTempo = TapTempo()
    private var loadFailed = false
    private val analysis=Analysis(app)
    private var activationVersion=0L
    private var readyActivation=0L
    private var waveJob:Job?=null
    private var workJob:Job?=null
    init {
        rhythm.onLinkedProjectChange={ next ->
            if(library.value.current?.id==next.id)update { current ->
                current.copy(bpm=next.bpm,beats=next.beats,denominator=next.denominator,
                    click=next.click,clickFollow=next.clickFollow)
            }
        }
        viewModelScope.launch { library.map { it.current }.distinctUntilChanged().collect(rhythm::syncProject) }
        viewModelScope.launch {
            rhythm.state.map { it.independentWanted }.distinctUntilChanged().collect { active ->
                if(active)app.startForegroundService(Intent(app,PlaybackService::class.java).setAction("rhythm"))
            }
        }
        viewModelScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) {
                    store.load().let { loaded ->
                        if(!prefs.getBoolean("smartClickOptInV20",false)) {
                            val projects=loaded.map { it.copy(smartClick=false) }
                            if(projects!=loaded)store.save(projects)
                            prefs.edit().putBoolean("smartClickOptInV20",true).apply()
                            projects
                        } else loaded
                    }.also { projects ->
                        if(!engine.state.value.playing && !engine.state.value.preparing) { PlaybackCache(app).prune(projects);PitchRenderer(app).prune(projects,engine.state.value.projectId.ifBlank { prefs.getString("selected","") ?: "" }) }
                        runCatching { RetiredStorageCleanup.clean(app.filesDir,projects.flatMap { it.stems }.flatMap { listOf(it.pcm,it.pitchFile) }.toSet()) }
                        val retained = projects.map { it.id }.toSet() + engine.state.value.projectId
                        val audioRoot = File(app.filesDir, "audio").canonicalFile
                        audioRoot.listFiles()?.filter { it.isDirectory && it.name !in retained }?.forEach { candidate ->
                            if (candidate.canonicalFile.parentFile == audioRoot) candidate.deleteRecursively()
                        }
                        val artworkRoot = File(app.filesDir, "artwork").canonicalFile
                        val pictures = projects.map { it.artwork }.toSet()
                        artworkRoot.listFiles()?.filter { "artwork/${it.name}" !in pictures }?.forEach { candidate ->
                        if (candidate.canonicalFile.parentFile == artworkRoot) candidate.delete()
                        }
                        for(folder in listOf("pitch","analysis")) {
                            val root=File(app.filesDir,folder).canonicalFile
                            root.listFiles()?.filter { it.isDirectory && it.name !in retained }?.forEach { candidate ->
                                if(candidate.canonicalFile.parentFile==root)candidate.deleteRecursively()
                            }
                        }
                    }
                }
                crossSaved.value=withContext(Dispatchers.IO) { runCatching { crossStore.load() }.getOrDefault(emptyList()) }
                val previous = prefs.getString("selected", "")
                val selected = projects.find { it.id == previous } ?: projects.firstOrNull()
                mutable.value = LibraryState(projects, selected?.id ?: "", busy = null)
                selected?.let { if (playback.value.projectId != it.id) activate(it) }
            } catch (e: Exception) {
                loadFailed = true
                mutable.value = LibraryState(busy = null, message = "Não foi possível abrir a biblioteca. Seus arquivos foram preservados. ${e.message}")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (projects in saves) {
                try {
                    store.save(projects)

                }
                catch (e: Exception) { withContext(Dispatchers.Main) { message("Não foi possível salvar: ${e.message}. Libere espaço antes de fechar o app.") } }
            }
        }
    }
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for(entries in crossSaves)runCatching { crossStore.save(entries) }.onFailure { withContext(Dispatchers.Main) { message("Não foi possível salvar o CrossMusic: ${it.message}") } }
        }
        viewModelScope.launch {
            var completion=playback.value.completionSerial
            playback.collect { state ->
                if(state.completionSerial!=completion) {
                    completion=state.completionSerial
                    val cross=crossMutable.value
                    if(cross.running && !cross.preparing && state.completedProjectId==cross.projectId)advanceCrossMusic(cross.index+1)
                }
            }
        }
    }
    fun consumeEditRequest() { mutable.update { it.copy(editRequestedId=null) } }
    fun saveCrossMusic(entries:List<CrossEntry>) {
        val saved=entries.distinctBy { it.projectId }
        crossSaved.value=saved;crossSaves.trySend(saved)
    }
    fun startCrossMusic(entries:List<CrossEntry>) {
        if(crossStartJob?.isActive==true || library.value.busy!=null || workJob?.isActive==true) { message("Aguarde a preparação atual antes de iniciar o CrossMusic.");return }
        val valid=entries.distinctBy { it.projectId }.filter { e->library.value.projects.any { it.id==e.projectId && it.stems.isNotEmpty() } }
        if(valid.isEmpty()) { message("Selecione pelo menos um projeto para o CrossMusic.");return }
        valid.forEach { e ->
            val p=library.value.projects.first { it.id==e.projectId }
            if(e.pad.enabled && PadKey.resolve(p,e.pad)==null) { message("Defina a tonalidade do projeto '${p.name}' ou escolha um tom para seu PAD.");return }
            if(e.tracks.values.any { !it.volume.isFinite() || it.volume !in 0f..3.162278f }) { message("Volume inválido no CrossMusic.");return }
        }
        saveCrossMusic(valid);engine.pause();runEntries=valid
        crossMutable.value=CrossMusicState(running=true,total=valid.size)
        advanceCrossMusic(0)
    }
    // Ending automation must never pause transport, cancel a DSP render, or stop the PAD.
    fun endCrossMusic() { crossMutable.update { it.copy(running=false) } }
    private fun advanceCrossMusic(index:Int) {
        if(!crossMutable.value.running)return
        if(index>=runEntries.size) {
            crossMutable.update { it.copy(running=false,preparing=false) }
            if(crossPadOwned)pad.stop()
            crossPadOwned=false;return
        }
        val entry=runEntries[index]
        crossMutable.update { it.copy(index=index,projectId=entry.projectId,preparing=true) }
        autoPlayRequested=true
        crossStartJob=viewModelScope.launch {
            var committed=false
            try {
                while(workJob?.isActive==true)delay(30)
                if(!crossMutable.value.running)return@launch
                val source=library.value.projects.firstOrNull { it.id==entry.projectId } ?: error("Projeto não encontrado na biblioteca.")
                val project=entry.apply(source).copy(loop=false)
                persist(library.value.projects.map { if(it.id==project.id)project else it })
                mutable.update { it.copy(selected=project.id) };prefs.edit().putString("selected",project.id).apply()
                committed=true;activate(project)
                val activation=activationVersion
                withTimeout(600_000) {
                    while(readyActivation!=activation || !playback.value.ready || playback.value.preparing || playback.value.projectId!=project.id) {
                        playback.value.error?.let { error(it) }
                        library.value.message?.let { error(it) }
                        if(!playback.value.preparing && !playback.value.ready && playback.value.preparation.any { it.status=="ERROR" })error("Preparação cancelada ou incompleta.")
                        if(library.value.selected!=project.id)return@withTimeout
                        delay(30)
                    }
                }
                if(library.value.selected!=project.id)return@launch
                configureCrossPad(project,entry)
                if(autoPlayRequested)play()
            } catch(e:TimeoutCancellationException) {
                crossMutable.update { it.copy(running=false) };message("CrossMusic: o preparo não foi concluído. Confira os arquivos e tente novamente.")
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                crossMutable.update { it.copy(running=false) };message("CrossMusic: ${e.message}")
            } finally {
                if(committed || !crossMutable.value.running)crossMutable.update { it.copy(preparing=false) }
            }
        }
    }
    private fun configureCrossPad(project:Project,entry:CrossEntry) {
        if(!entry.pad.enabled) { if(crossPadOwned)pad.stop();crossPadOwned=false;return }
        val key=PadKey.resolve(project,entry.pad) ?: return
        pad.configure(note=key.first,minor=key.second,sound=entry.pad.sound,volume=entry.pad.volume,transitionFrames=144000)
        pad.play();crossPadOwned=true
    }
    private fun followCrossPad(project:Project) {
        val cross=crossMutable.value
        if(!cross.running || cross.projectId!=project.id)return
        runEntries.getOrNull(cross.index)?.takeIf { it.pad.enabled && it.pad.followProjectKey }?.let { configureCrossPad(project,it) }
    }
    fun message(value: String?) { mutable.value = mutable.value.copy(message = value) }
    fun dismissError() { engine.clearError(); message(null) }
    fun dismissImportReport() { mutable.update { it.copy(imports=emptyList()) } }
    private fun persist(projects: List<Project>) {
        if (loadFailed) { message("A biblioteca não foi carregada. Reinicie após corrigir o armazenamento."); return }
        mutable.value = mutable.value.copy(projects = projects)
        saves.trySend(projects)
    }
    fun update(transform: (Project) -> Project) {
        val old = library.value.current ?: return
        val next = old.protectGains(transform(old))
        persist(library.value.projects.map { if (it.id == next.id) next else it })
        engine.update(next)
        if(next.key!=old.key || next.semitones!=old.semitones || next.targetKey!=old.targetKey)followCrossPad(next)
    }
    fun lockFader(id:String) = update { it.toggleGainLock(id) }
    fun stem(id: String, transform: (Stem) -> Stem) = update { p -> p.copy(stems = p.stems.map { if (it.id == id) transform(it) else it }) }
    fun select(p: Project) {
        if (library.value.busy != null || p.id == library.value.selected) return
        if(library.value.background?.startsWith("Preparando")==true) { message("Aguarde a preparação terminar antes de trocar de projeto.");return }
        autoPlayRequested=false
        if(crossMutable.value.running) {
            val index=runEntries.indexOfFirst { it.projectId==p.id }
            crossMutable.update { it.copy(projectId=p.id,index=if(index>=0)index else it.index,preparing=false) }
        }
        mutable.value = mutable.value.copy(selected = p.id)
        prefs.edit().putString("selected", p.id).apply()
        activate(p)
    }
    private fun activate(p:Project) {
        val activation=++activationVersion
        waveJob?.cancel()
        mutable.value=mutable.value.copy(waveform=emptyList())
        waveJob=viewModelScope.launch {
            try {
                val updated=withContext(Dispatchers.IO) {
                    val stems=p.stems.map { s ->
                        if(!s.external)s else {
                            val fingerprint=SourceFingerprint.get(getApplication(),Uri.parse(s.source))
                            if(fingerprint==s.fingerprint)s else {
                                val fresh=AudioImporter(getApplication()).import(listOf(Uri.parse(s.source)),p.name) {}
                                val meta=fresh.stems.single()
                                s.copy(frames=(meta.frames.toDouble()*p.sampleRate/fresh.sampleRate).roundToLong(),sourceRate=fresh.sampleRate,sourceFrames=meta.frames,format=meta.format,external=meta.external,compressed=meta.compressed,pcm=meta.pcm,fingerprint=fingerprint,pitchFile="",pitchApplied=0,dspSpeed=100)
                            }
                        }
                    }
                    if(stems!=p.stems)p.copy(stems=stems.map { it.copy(pitchFile="",pitchApplied=0,dspSpeed=100) },detectedBpm=0.0,confidence=0.0,analysisKey="",semitones=0,targetKey="",pitchTracks=emptyList(),speed=100,speedTracks=null) else p
                }
                if(library.value.selected!=p.id)return@launch
                val latest=library.value.current ?: return@launch
                val ready=if(updated==p)latest else updated.copy(name=latest.name,master=latest.master,masterPan=latest.masterPan,masterMute=latest.masterMute,
                    stems=latest.stems.map { live -> updated.stems.first { it.id==live.id }.copy(volume=live.volume,pan=live.pan,mute=live.mute,solo=live.solo,bus=live.bus,route=live.route,color=live.color) },dcas=latest.dcas,buses=latest.buses)
                if(ready!=latest)persist(library.value.projects.map { if(it.id==p.id)ready else it })
                val prepared=if(ready.stems.any { s -> s.pitchFile.isNotBlank() && File(getApplication<Application>().filesDir,s.pitchFile).length()!=kotlin.math.round(s.frames/(s.dspSpeed/100.0)).toLong()*8 }) {
                    mutable.update { it.copy(background="Preparando ${ready.speed}%…") }
                    try { withContext(Dispatchers.IO) { ready.copy(stems=PitchRenderer(getApplication()).render(ready,ready.semitones,ready.pitchTracks) { text->mutable.update { it.copy(background=text) } }) } }
                    finally { mutable.update { it.copy(background=null) } }
                } else ready
                if(prepared!=ready)persist(library.value.projects.map { if(it.id==p.id)prepared else it })
                engine.load(prepared)
                while(playback.value.preparing || !playback.value.ready || playback.value.projectId!=ready.id) {
                    if(playback.value.error!=null)return@launch
                    delay(50)
                }
                if(activationVersion==activation)readyActivation=activation
                withContext(Dispatchers.IO) {
                    analysis.waveform(ready,{ !playback.value.playing }) { peaks -> mutable.update { if(it.selected==p.id)it.copy(waveform=peaks) else it } }
                }
                if(!ready.keyAnalyzed && ready.key.trim() in listOf("","—","-")) {
                    val estimate=withContext(Dispatchers.Default) { analysis.musicalKey(ready) }
                    val current=library.value.current
                    if(current?.id==ready.id && !current.keyAnalyzed && current.key.trim() in listOf("","—","-"))update { it.copy(key=estimate?.name ?: it.key,keyAnalyzed=true) }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { message("Não foi possível ler/analisar a origem: ${e.message}") }
        }
    }
    fun reorderProject(id:String,to:Int) {
        val list=library.value.projects.toMutableList();val from=list.indexOfFirst { it.id==id }
        if(from>=0) { list.add(to.coerceIn(0,list.lastIndex),list.removeAt(from));persist(list) }
    }
    fun moveProject(id: String, delta: Int) {
        val list = library.value.projects.toMutableList(); val from = list.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from >= 0 && from != to) { val p = list.removeAt(from); list.add(to, p); persist(list) }
    }
    fun moveStem(id: String, delta: Int) {
        update { p ->
            val list = p.stems.toMutableList(); val from = list.indexOfFirst { it.id == id }; val to = (from + delta).coerceIn(0, list.lastIndex)
            if (from >= 0) { val s = list.removeAt(from); list.add(to, s) }
            p.copy(stems = list)
        }
    }
    fun reorderStem(id:String,to:Int) {
        update { p->val list=p.stems.toMutableList(); val from=list.indexOfFirst { it.id==id }
            if(from>=0)list.add(to.coerceIn(0,list.lastIndex),list.removeAt(from))
            p.copy(stems=list)
        }
    }
    fun delete(p: Project) {
        if(crossMutable.value.running && runEntries.any { it.projectId==p.id }) { message("Encerre o CrossMusic antes de excluir um projeto da sequência.");return }
        if (library.value.busy != null) return
        val remaining = library.value.projects.filterNot { it.id == p.id }
        if (library.value.selected == p.id) {
            waveJob?.cancel()
            engine.unload()
            mutable.value = mutable.value.copy(selected = "")
        }
        persist(remaining)
        remaining.firstOrNull()?.let { if (library.value.selected.isEmpty()) select(it) }
        // PCM is retained until a later startup cleanup to avoid deleting a file still open by the mixer.
    }
    fun import(uris: List<Uri>? = null, tree: Uri? = null) {
        if (library.value.busy != null || loadFailed) return
        engine.pause()
        mutable.value = mutable.value.copy(busy = "Lendo arquivos…",imports=emptyList())
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) {
                    val importer = AudioImporter(getApplication())
                    val files = uris ?: importer.folder(tree!!)
                    val metadata=ImportMetadata(getApplication())
                    val title=metadata.folderName(files,tree)
                    val imported=importer.import(files, title,tree,allowPartial=true,onItem={ item -> mutable.update { state -> state.copy(imports=(state.imports.filterNot { it.index==item.index }+item).sortedBy { it.index }) } }) { text -> mutable.update { it.copy(busy = text) } }
                    imported.copy(artwork=if(tree!=null)runCatching { metadata.randomArtwork(tree,imported.id) }.getOrDefault("") else "")
                }
                persist(library.value.projects + project)
                mutable.value = mutable.value.copy(busy = null)
                select(project)
                mutable.update { it.copy(editRequestedId=project.id) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message("Importação não concluída: ${e.message ?: "arquivo incompatível ou sem permissão"}")
            } finally { mutable.value = mutable.value.copy(busy = null) }
        }
    }
    fun artwork(uri: Uri) {
        val id = library.value.selected
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    ImportMetadata(getApplication()).saveArtwork(uri,id)
                }
                if (library.value.selected == id) update { it.copy(artwork = path) }
            } catch (e: Exception) { message("Não foi possível importar a imagem: ${e.message}") }
        }
    }
    fun retryPreparation() { library.value.current?.let(::activate) }
    fun cancelPreparation()=engine.cancelPreparation()
    fun removeTrack(id:String) {
        val p=library.value.current ?: return
        engine.cancelPreparation()
        val next=p.copy(stems=p.stems.filterNot { it.id==id },dcas=p.dcas.map { it.copy(members=it.members-id) })
        if(next.stems.isEmpty()) { delete(p);return }
        persist(library.value.projects.map { if(it.id==p.id)next else it });activate(next)
    }
    private var rhythmPlayJob:Job?=null
    fun play() {
        if (library.value.busy != null || library.value.current == null) return
        if(playback.value.projectId!=library.value.selected || !playback.value.ready || playback.value.preparing) { message("Preparando a saída de áudio. Tente novamente em instantes."); return }
        val app = getApplication<Application>()
        rhythmPlayJob?.cancel()
        rhythmPlayJob=viewModelScope.launch {
            val selected=library.value.selected
            rhythm.syncProject(library.value.current)
            val waitForPair=rhythm.state.value.let { it.linked && it.percussion.enabled && it.percussion.follow }
            if(waitForPair) {
                val ready=rhythm.state.first { !it.preparing || !it.percussion.enabled }
                if(!ready.percussion.enabled || ready.error!=null || !ready.soundReady)return@launch
            }
            if(selected==library.value.selected)app.startForegroundService(Intent(app, PlaybackService::class.java).setAction("play"))
        }
    }
    fun pause() { rhythmPlayJob?.cancel();autoPlayRequested=false;engine.pause() }
    fun pauseForInterruption(reason:String) { rhythmPlayJob?.cancel();autoPlayRequested=false;engine.pause(reason);pad.stop();rhythm.stopAll() }
    fun refreshOutput() { library.value.current?.let { engine.pause(); engine.load(it,true) } }
    fun stop() {
        rhythmPlayJob?.cancel()
        autoPlayRequested=false
        engine.stop()
        val app = getApplication<Application>()
        if(!rhythm.state.value.independentWanted)app.stopService(Intent(app, PlaybackService::class.java))
    }
    fun seek(frame: Long) = engine.seek(frame)
    fun tap() { tapTempo.tap(SystemClock.elapsedRealtime())?.let { bpm -> update { it.copy(bpm = bpm) } } }
    fun adjacent(delta: Int) {
        val list = library.value.projects; val index = list.indexOfFirst { it.id == library.value.selected }
        list.getOrNull(index + delta)?.let(::select)
    }
    fun bothAll() = update { p -> p.copy(stems=p.stems.map { it.copy(bus="",route="BOTH") },clickRoute="BOTH") }
    fun split() = update { p -> p.copy(stems=p.stems.map { s -> s.copy(bus="",route=if(listOf("click","guide","guia","clk","metro").any { s.name.contains(it,true) })"LEFT" else "RIGHT") },clickRoute="LEFT") }
    fun smartClick(enabled:Boolean) {
        if(enabled && (!playback.value.ready || playback.value.preparing)) { message("Aguarde a preparação do áudio antes do Smart Click.");return }
        update { it.copy(smartClick=enabled) }
        if(enabled)analyzeTempo()
    }
    fun analyzeTempo(force:Boolean=false) {
        val p=library.value.current ?: return
        if(playback.value.projectId!=p.id || !playback.value.ready || playback.value.preparing) { message("Aguarde a preparação do áudio antes do Smart Click.");return }
        if(workJob?.isActive==true)return
        if(!force && p.analysisKey==analysis.key(p) && p.detectedBpm>0) { if(p.smartClick)update { it.copy(bpm=p.detectedBpm) };return }
        workJob=viewModelScope.launch {
            mutable.update { it.copy(background="Smart Click: analisando ${TempoDetector.reference(p.stems).name}…") }
            try {
                val result=withContext(Dispatchers.IO) { analysis.tempo(p) }
                persist(library.value.projects.map { if(it.id==p.id)it.copy(detectedBpm=result.bpm,confidence=result.confidence,analysisKey=analysis.key(p),bpm=if(it.smartClick && result.bpm in 30.0..300.0)result.bpm else it.bpm) else it })
                library.value.current?.takeIf { it.id==p.id }?.let { engine.update(it) }
                if(result.bpm==0.0)message("Não foram encontradas batidas suficientemente regulares. Ajuste o BPM manualmente.")
            } catch(e:Exception) { if(e is CancellationException)throw e; message("Falha na análise: ${e.message}") }
            finally { mutable.update { it.copy(background=null) } }
        }
    }
    fun applySpeed(percent:Int,ids:List<String>,preset:Int=percent) {
        val p=library.value.current ?: return
        applyDsp(p.key,p.targetKey,p.semitones,p.pitchTracks,percent,ids,preset)
    }
    fun applyPitch(origin:String,destination:String,semitones:Int,ids:List<String>) {
        val p=library.value.current ?: return
        applyDsp(origin,destination,semitones,ids,p.speed,p.selectedSpeedTracks,p.speedPreset)
    }
    private fun applyDsp(origin:String,destination:String,semitones:Int,ids:List<String>,speed:Int,speedIds:List<String>,preset:Int) {
        val p=library.value.current ?: return
        if(workJob?.isActive==true)return
        if(playback.value.preparing) { message("Aguarde a preparação do projeto antes de aplicar a alteração.");return }
        workJob=viewModelScope.launch {
            mutable.update { it.copy(background="Preparando $speed%…") }
            try {
                val rendered=withContext(Dispatchers.IO) { PitchRenderer(getApplication()).render(p.copy(speed=speed,speedTracks=speedIds),semitones,ids) { text->mutable.update { it.copy(background=text) } } }
                fun changed(current:Project)=current.copy(key=origin,targetKey=destination,semitones=semitones,pitchTracks=ids,speed=speed,speedTracks=speedIds,speedPreset=preset,stems=current.stems.map { s ->
                    val r=rendered.first { it.id==s.id }; s.copy(pitchFile=r.pitchFile,pitchApplied=r.pitchApplied,dspSpeed=r.dspSpeed)
                })
                val needsLoad=playback.value.projectId!=p.id || !playback.value.ready
                if(library.value.selected==p.id && !needsLoad)library.value.current?.let { engine.applyPreparedPitch(changed(it)) }
                persist(library.value.projects.map { if(it.id==p.id)changed(it) else it })
                if(library.value.selected==p.id)library.value.current?.let(::followCrossPad)
                if(library.value.selected==p.id && needsLoad)library.value.current?.let(::activate)
                withContext(Dispatchers.IO) { PitchRenderer(getApplication()).prune(library.value.projects,library.value.selected) }
            } catch(e:Exception) { if(e is CancellationException)throw e; message("Alteração não aplicada. ${e.message} Abra TOM ou VELOCIDADE para tentar novamente ou retornar ao original.") }
            finally { mutable.update { it.copy(background=null) } }
        }
    }
}
