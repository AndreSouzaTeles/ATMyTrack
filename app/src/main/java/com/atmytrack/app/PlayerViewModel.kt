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
    val background:String?=null,val waveform:List<Float> = emptyList(),val imports:List<ImportItem> = emptyList()) {
    val current get() = projects.find { it.id == selected }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val separation=com.atmytrack.app.separation.SeparationController(app)
    fun startSeparation(uri:Uri,title:String) {
        val status=separation.state.value
        if(!status.connected || !status.installed || status.busy)return
        if(library.value.busy!=null || library.value.background!=null || playback.value.preparing) { message("Aguarde a preparação/importação atual terminar.");return }
        waveJob?.cancel()
        com.atmytrack.app.separation.SeparationLock.requested=true
        stop();pad.stop()
        separation.command(3,uri.toString(),title)
    }
    fun importSeparation(intoCurrent:Boolean) {
        if(library.value.busy!=null || separation.state.value.busy || loadFailed)return
        val id=separation.state.value.result
        if(!id.matches(Regex("[a-f0-9-]{36}")))return
        val dir=File(getApplication<Application>().filesDir,"separation/results/$id")
        val current=library.value.current
        if(intoCurrent && (current==null || current.stems.size>62 || current.semitones!=0 || current.speed!=100)) {
            message("Use um novo projeto ou um projeto com até 62 tracks, tom original e velocidade 100% para adicionar stems alinhadas.");return
        }
        stop();pad.stop()
        mutable.update { it.copy(busy="Importando stems separadas…") }
        viewModelScope.launch {
            try {
                val project=withContext(Dispatchers.IO) {
                    check(File(dir,"complete").isFile) { "Separação incompleta. Tente novamente." }
                    val frames=File(dir,"complete").readLines().first().toLong()
                    val rate=if(intoCurrent)current!!.sampleRate else 44100
                    val tracks=listOf("Voz","Instrumental").map { name ->
                        val file=File(dir,"$name.wav")
                        check(file.length()==44+frames*8) { "Stem incompleta: $name" }
                        Stem(name=name,pcm="separation/results/$id/$name.wav",frames=(frames.toDouble()*rate/44100).roundToLong(),format=PcmFormat(offset=44),sourceRate=44100,sourceFrames=frames)
                    }
                    if(intoCurrent)current!!.copy(stems=current.stems+tracks) else Project(name=File(dir,"title.txt").readText().ifBlank { "Música separada" },sampleRate=44100,stems=tracks)
                }
                val next=if(intoCurrent)library.value.projects.map { if(it.id==project.id)project else it } else library.value.projects+project
                // The existing save queue owns all library writes; no schema change.
                persist(next)
                separation.consumed()
                mutable.update { it.copy(busy=null,selected=project.id) }
                prefs.edit().putString("selected",project.id).apply();activate(project)
            } catch(e:Exception) { message("Não foi possível importar: ${e.message}") }
            finally { mutable.update { it.copy(busy=null) } }
        }
    }
    override fun onCleared() { separation.close();super.onCleared() }
    fun discardSeparation() {
        val id=separation.state.value.result
        if(separation.state.value.busy || !id.matches(Regex("[a-f0-9-]{36}")))return
        if(library.value.projects.none { p -> p.stems.any { it.pcm.startsWith("separation/results/$id/") } }) {
            viewModelScope.launch(Dispatchers.IO) { File(getApplication<Application>().filesDir,"separation/results/$id").deleteRecursively() }
        }
        separation.consumed()
    }
    private val engine = (app as TrackApplication).engine
    val pad = (app as TrackApplication).pad
    val playback = engine.state
    private val store = ProjectStore(app.filesDir)
    private val prefs = app.getSharedPreferences("settings", 0)
    private val mutable = MutableStateFlow(LibraryState())
    val library = mutable.asStateFlow()
    private val saves = Channel<List<Project>>(Channel.CONFLATED)
    private val tapTempo = TapTempo()
    private var loadFailed = false
    private val analysis=Analysis(app)
    private var waveJob:Job?=null
    private var workJob:Job?=null
    init {
        viewModelScope.launch {
            try {
                val projects = withContext(Dispatchers.IO) {
                    store.load().also { projects ->
                        if(!engine.state.value.playing && !engine.state.value.preparing) { PlaybackCache(app).prune(projects);PitchRenderer(app).prune(projects,engine.state.value.projectId.ifBlank { prefs.getString("selected","") ?: "" }) }
                        if(!engine.state.value.playing && !engine.state.value.preparing) {
                            val used=projects.flatMap { it.stems }.map { it.pcm }.toSet()
                            val results=File(app.filesDir,"separation/results").canonicalFile
                            results.listFiles()?.filter { it.isDirectory && File(it,"consumed").isFile && used.none { path -> path.startsWith("separation/results/${it.name}/") } }?.forEach { child ->
                                if(child.canonicalFile.parentFile==results)child.deleteRecursively()
                            }
                        }
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
                    projects.flatMap { it.stems }.mapNotNull { Regex("^separation/results/([a-f0-9-]{36})/").find(it.pcm)?.groupValues?.get(1) }.distinct().forEach { id ->
                        File(app.filesDir,"separation/results/$id/consumed").writeText("saved")
                    }
                }
                catch (e: Exception) { withContext(Dispatchers.Main) { message("Não foi possível salvar: ${e.message}. Libere espaço antes de fechar o app.") } }
            }
        }
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
    }
    fun lockFader(id:String) = update { it.toggleGainLock(id) }
    fun stem(id: String, transform: (Stem) -> Stem) = update { p -> p.copy(stems = p.stems.map { if (it.id == id) transform(it) else it }) }
    fun select(p: Project) {
        if (library.value.busy != null || p.id == library.value.selected) return
        if(library.value.background?.startsWith("Preparando")==true) { message("Aguarde a preparação terminar antes de trocar de projeto.");return }
        mutable.value = mutable.value.copy(selected = p.id)
        prefs.edit().putString("selected", p.id).apply()
        activate(p)
    }
    private fun activate(p:Project) {
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
                    stems=latest.stems.map { live -> updated.stems.first { it.id==live.id }.copy(volume=live.volume,pan=live.pan,mute=live.mute,solo=live.solo,bus=live.bus,route=live.route) },dcas=latest.dcas,buses=latest.buses)
                if(ready!=latest)persist(library.value.projects.map { if(it.id==p.id)ready else it })
                val prepared=if(ready.stems.any { s -> s.pitchFile.isNotBlank() && File(getApplication<Application>().filesDir,s.pitchFile).length()!=kotlin.math.round(s.frames/(s.dspSpeed/100.0)).toLong()*8 }) {
                    mutable.update { it.copy(background="Preparando ${ready.speed}%…") }
                    try { withContext(Dispatchers.IO) { ready.copy(stems=PitchRenderer(getApplication()).render(ready,ready.semitones,ready.pitchTracks) { text->mutable.update { it.copy(background=text) } }) } }
                    finally { mutable.update { it.copy(background=null) } }
                } else ready
                if(prepared!=ready)persist(library.value.projects.map { if(it.id==p.id)prepared else it })
                engine.load(prepared)
                while(playback.value.preparing || playback.value.projectId!=ready.id) {
                    if(playback.value.error!=null)return@launch
                    delay(50)
                }
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
    fun play() {
        if(separation.state.value.busy) { message("Aguarde a separação ou cancele antes de tocar ao vivo.");return }
        if (library.value.busy != null || library.value.current == null) return
        if(playback.value.projectId!=library.value.selected || !playback.value.ready || playback.value.preparing) { message("Preparando a saída de áudio. Tente novamente em instantes."); return }
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, PlaybackService::class.java).setAction("play"))
    }
    fun pause() = engine.pause()
    fun refreshOutput() { library.value.current?.let { engine.pause(); engine.load(it,true) } }
    fun stop() {
        engine.stop()
        val app = getApplication<Application>()
        app.stopService(Intent(app, PlaybackService::class.java))
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
                if(library.value.selected==p.id && needsLoad)library.value.current?.let(::activate)
                withContext(Dispatchers.IO) { PitchRenderer(getApplication()).prune(library.value.projects,library.value.selected) }
            } catch(e:Exception) { if(e is CancellationException)throw e; message("Alteração não aplicada. ${e.message} Abra TOM ou VELOCIDADE para tentar novamente ou retornar ao original.") }
            finally { mutable.update { it.copy(background=null) } }
        }
    }
}
