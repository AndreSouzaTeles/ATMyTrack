package com.atmytrack.app.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.AtomicFile
import com.atmytrack.app.data.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

data class PercussionSettings(val source:String=PercussionSynth.patterns.first(),val bpm:Double=100.0,val beats:Int=4,val denominator:Int=4,
    val bars:Int=1,val volume:Float=.4f,val follow:Boolean=false,val enabled:Boolean=false)
data class ImportedBeat(val id:String,val name:String,val frames:Int)
data class RhythmState(val percussion:PercussionSettings=PercussionSettings(),val metronome:Project=Project(id="standalone-metronome",name="Metrônomo",sampleRate=48000,stems=emptyList(),bpm=100.0,clickFollow=false),
    val linked:Boolean=true,val metronomeArmed:Boolean=false,val imports:List<ImportedBeat> = emptyList(),val loading:Boolean=false,val preparing:Boolean=false,val soundReady:Boolean=false,val error:String?=null) {
    val independentWanted get()=(metronomeArmed && metronome.click && !metronome.clickFollow) || (percussion.enabled && !percussion.follow)
}
data class PercussionSpec(val source:String,val bpm:Double,val beats:Int,val denominator:Int,val bars:Int)
private data class PreparedBeat(val spec:PercussionSpec,val audio:FloatArray)

/** A separate clock only for independent sounds; followed percussion uses the multitrack mixer clock. */
class RhythmEngine(private val context:Context) {
    private val acousticKit by lazy { AcousticKit.load { context.assets.open(it) } }
    var onLinkedProjectChange:((Project)->Unit)?=null
    private val clockRevision=AtomicInteger()
    private val prefs=context.getSharedPreferences("rhythm",0)
    private val root=File(context.filesDir,"rhythm").apply { mkdirs() }
    private val mutable=MutableStateFlow(RhythmState(preparing=true))
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"ATMyTrack-Rhythm") }
    private val generation=AtomicInteger()
    private var workerRunning=false
    @Volatile private var focusGranted=false
    private var preparation:Job?=null
    @Volatile private var libraryReady=false
    private var requested:PercussionSpec?=null
    @Volatile private var prepared:PreparedBeat?=null
    @Volatile private var project:Project?=null
    @Volatile var renderedIndependentFrames=0L;private set
    init {
        val percussion=PercussionSettings(source=prefs.getString("source",PercussionSynth.patterns.first())!!,bpm=prefs.getFloat("bpm",100f).toDouble(),
            beats=prefs.getInt("beats",4),denominator=prefs.getInt("denominator",4),bars=prefs.getInt("bars",1),volume=prefs.getFloat("volume",.4f),follow=prefs.getBoolean("follow",false))
        val metro=state.value.metronome.copy(bpm=prefs.getFloat("metroBpm",100f).toDouble(),beats=prefs.getInt("metroBeats",4),
            denominator=prefs.getInt("metroDenominator",4),clickVolume=prefs.getFloat("metroVolume",.35f),clickSound=prefs.getString("metroSound","Classic")!!,
            multiplier=prefs.getFloat("metroMultiplier",1f).toDouble(),accent=prefs.getBoolean("metroAccent",true),clickRoute=prefs.getString("metroRoute","BOTH")!!)
        mutable.value=RhythmLink.fromMetronome(state.value.copy(linked=prefs.getBoolean("linked",true),percussion=percussion),metro)
        scope.launch(Dispatchers.IO) {
            val imports=runCatching {
                val array=JSONArray(AtomicFile(File(root,"library.json")).openRead().bufferedReader().use { it.readText() })
                (0 until array.length()).map { i -> val j=array.getJSONObject(i);ImportedBeat(j.getString("id"),j.getString("name"),j.getInt("frames")) }
                    .filter { it.id.matches(Regex("[a-f0-9-]{36}")) && File(root,"${it.id}.pcm").length()==it.frames*8L }
            }.getOrDefault(emptyList())
            mutable.update { it.copy(imports=imports) };libraryReady=true;prepare()
        }
    }
    @Synchronized fun syncProject(value:Project?) {
        val changed=project?.id!=value?.id;project=value
        if(value!=null)applyState(RhythmLink.fromMetronome(state.value,value,if(changed)false else state.value.metronomeArmed))
        else if(changed)applyState(RhythmLink.fromMetronome(state.value,RhythmState().metronome,false))
        prepare();refreshWorker()
    }
    fun configureMetronome(transform:(Project)->Project) {
        val p=transform(state.value.metronome)
        applyState(RhythmLink.fromMetronome(state.value,p).copy(error=null))
        prefs.edit().putFloat("metroBpm",p.bpm.toFloat()).putInt("metroBeats",p.beats).putInt("metroDenominator",p.denominator)
            .putFloat("metroVolume",p.clickVolume).putString("metroSound",p.clickSound).putFloat("metroMultiplier",p.multiplier.toFloat())
            .putBoolean("metroAccent",p.accent).putString("metroRoute",p.clickRoute).apply()
        saveLinked();prepare();refreshWorker()
    }
    fun armMetronome(enabled:Boolean) { applyState(RhythmLink.fromMetronome(state.value,state.value.metronome.copy(click=enabled),enabled).copy(error=null));saveLinked();prepare();refreshWorker() }
    fun audioFocus(granted:Boolean) { focusGranted=granted;refreshWorker() }
    @Synchronized fun configure(transform:(PercussionSettings)->PercussionSettings) {
        val p=transform(state.value.percussion)
        require(p.bpm.isFinite() && p.bpm in 30.0..300.0 && p.beats in 1..12 && p.denominator in listOf(4,8) && p.bars in 1..64 && p.volume.isFinite() && p.volume in 0f..1f)
        applyState(RhythmLink.fromPercussion(state.value,p).copy(error=null))
        prefs.edit().putString("source",p.source).putFloat("bpm",p.bpm.toFloat()).putInt("beats",p.beats).putInt("denominator",p.denominator)
            .putInt("bars",p.bars).putFloat("volume",p.volume).putBoolean("follow",p.follow).apply()
        saveLinked();prepare();refreshWorker()
    }
    private fun applyState(next:RhythmState) {
        val old=state.value
        if(next.linked && (old.linked!=next.linked || old.metronome.bpm!=next.metronome.bpm || old.metronome.clickFollow!=next.metronome.clickFollow || old.percussion.enabled!=next.percussion.enabled || old.metronome.beats!=next.metronome.beats || old.metronome.denominator!=next.metronome.denominator))clockRevision.incrementAndGet()
        mutable.value=next
    }
    private fun saveLinked() {
        val s=state.value
        prefs.edit().putBoolean("linked",s.linked).putFloat("bpm",s.percussion.bpm.toFloat()).putBoolean("follow",s.percussion.follow)
            .putInt("beats",s.percussion.beats).putInt("denominator",s.percussion.denominator)
            .putFloat("metroBpm",s.metronome.bpm.toFloat()).putInt("metroBeats",s.metronome.beats).putInt("metroDenominator",s.metronome.denominator).apply()
        if(s.linked && project!=null && project!=s.metronome) { project=s.metronome;onLinkedProjectChange?.invoke(s.metronome) }
    }
    @Synchronized fun setLinked(enabled:Boolean,fromPercussion:Boolean=false) {
        var next=state.value.copy(linked=enabled)
        if(enabled)next=if(fromPercussion)RhythmLink.fromPercussion(next,next.percussion) else RhythmLink.fromMetronome(next,next.metronome)
        applyState(next);saveLinked();prepare();refreshWorker()
    }
    private fun spec():PercussionSpec {
        val p=state.value.percussion;val music=project?.takeIf { p.follow }
        return PercussionSpec(p.source,music?.effectiveBpm ?: p.bpm,music?.beats ?: p.beats,music?.denominator ?: p.denominator,if(p.source in PercussionSynth.patterns)1 else p.bars)
    }
    @Synchronized private fun prepare() {
        if(!libraryReady)return
        val spec=spec()
        if(requested==spec && (prepared?.spec==spec || preparation?.isActive==true))return
        requested=spec;preparation?.cancel();prepared=null
        mutable.update { it.copy(preparing=true,soundReady=false) }
        preparation=scope.launch {
            try {
                val audio=if(spec.source in PercussionSynth.patterns)PercussionSynth.render(spec.source,spec.bpm,spec.beats,spec.denominator,kit=acousticKit)
                else {
                    val imported=state.value.imports.find { it.id==spec.source } ?: error("Selecione uma batida ou importe um loop.")
                    val original=withContext(Dispatchers.IO) { readPcm(File(root,"${imported.id}.pcm"),imported.frames) }
                    val target=PercussionSynth.barFrames(48000,spec.bpm,spec.beats,spec.denominator)*spec.bars
                    PercussionLoopStretch.render(original,target)
                }
                ensureActive()
                synchronized(this@RhythmEngine) { if(requested==spec) {
                    prepared=PreparedBeat(spec,audio)
                    mutable.update { it.copy(preparing=false,soundReady=true,error=null) };refreshWorker()
                } }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { synchronized(this@RhythmEngine) { if(requested==spec) {
                mutable.update { it.copy(preparing=false,soundReady=false,error=e.message ?: "Não foi possível preparar a percussão.",percussion=it.percussion.copy(enabled=false)) };refreshWorker()
            } } }
        }
    }
    fun importLoop(uri:Uri) {
        if(state.value.loading)return
        mutable.update { it.copy(loading=true,error=null) }
        scope.launch(Dispatchers.IO) {
            try {
                val importer=AudioImporter(context)
                val importedProject=importer.import(listOf(uri),"Loop de percussão",progress={})
                require(importedProject.seconds in .05..60.0) { "Importe um loop de percussão com duração entre 0,05 e 60 segundos." }
                val p=importedProject.copy(sampleRate=48000,stems=importedProject.stems.map { it.copy(frames=round(it.frames*48000.0/importedProject.sampleRate).toLong()) })
                val frames=p.frames.toInt();val samples=FloatArray(frames*2);val block=FloatArray(8192)
                PlaybackCache(context).open(p.stems.single(),p.sampleRate,{ensureActive()}).use { reader ->
                    var pos=0;while(pos<frames) { ensureActive();val count=minOf(4096,frames-pos);reader.read(pos.toLong(),count,block);block.copyInto(samples,pos*2,0,count*2);pos+=count }
                }
                // Normalize to 48 kHz and persist our own PCM outside project pruning.
                check(p.sampleRate==48000)
                val imported=ImportedBeat(UUID.randomUUID().toString(),importer.name(uri),frames)
                writePcm(File(root,"${imported.id}.pcm"),samples)
                val all=state.value.imports+imported
                val array=JSONArray(all.map { JSONObject().put("id",it.id).put("name",it.name).put("frames",it.frames) })
                val file=AtomicFile(File(root,"library.json"));val out=file.startWrite()
                try { out.write(array.toString().toByteArray());file.finishWrite(out) } catch(e:Exception) { file.failWrite(out);throw e }
                mutable.update { it.copy(imports=all,loading=false) }
                configure { it.copy(source=imported.id,enabled=false,bars=1) }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(loading=false,error="Não foi possível importar: ${e.message}") } }
        }
    }
    fun stopAll() {
        mutable.update { it.copy(metronomeArmed=false,metronome=it.metronome.copy(click=false),percussion=it.percussion.copy(enabled=false)) };refreshWorker()
    }
    fun stopIndependent() {
        mutable.update { it.copy(metronomeArmed=false,metronome=if(it.metronome.clickFollow)it.metronome else it.metronome.copy(click=false),
            percussion=if(it.percussion.follow)it.percussion else it.percussion.copy(enabled=false)) };refreshWorker()
    }
    fun close() { stopAll();scope.cancel();worker.shutdown() }
    /** Called before the main master gain/limiter, with the exact rendering clock (including seek/loop). */
    fun mixFollow(output:FloatArray,count:Int,start:Long,p:Project) {
        val settings=state.value.percussion;val loop=prepared ?: return
        if(!settings.enabled || !settings.follow || state.value.preparing || project?.id!=p.id)return
        if(abs(loop.spec.bpm-p.bpm)>.001 || loop.spec.beats!=p.beats || loop.spec.denominator!=p.denominator)return
        if(start<p.beatOffset) {
            val skip=(p.beatOffset-start).coerceAtMost(count.toLong()).toInt()
            // An offset inside this block is rare; add only its active suffix without allocating.
            for(i in skip until count) {
                val index=(((start+i-p.beatOffset)*48000L/p.sampleRate)%(loop.audio.size/2)).toInt()
                output[i*2]+=loop.audio[index*2]*settings.volume;output[i*2+1]+=loop.audio[index*2+1]*settings.volume
            }
        } else PercussionSynth.add(loop.audio,output,count,start-p.beatOffset,p.sampleRate,settings.volume)
    }
    @Synchronized private fun refreshWorker() {
        val wanted=state.value.independentWanted && focusGranted
        if(!wanted) { if(workerRunning){generation.incrementAndGet();workerRunning=false};return }
        if(workerRunning)return
        workerRunning=true;val token=generation.incrementAndGet()
        worker.execute {
            var track:AudioTrack?=null
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
                val buffer=maxOf(AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT),16384)
                track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()).setBufferSizeInBytes(buffer).build()
                check(track.state==AudioTrack.STATE_INITIALIZED)
                val output=FloatArray(1024);val limiter=PeakLimiter();var metroFrame=0L;var percussionFrame=0L;var revision=clockRevision.get()
                track.play()
                while(generation.get()==token) {
                    val state=state.value;val m=state.metronome;val percussion=state.percussion;val loop=prepared
                    if(state.linked && revision!=clockRevision.get()){revision=clockRevision.get();metroFrame=0;percussionFrame=0}
                    val pairReady=!state.linked || !percussion.enabled || (!state.preparing && loop!=null && loop.spec==spec())
                    output.fill(0f)
                    if(pairReady && state.metronomeArmed && m.click && !m.clickFollow) {
                        repeat(512) { i ->
                            val v=MixMath.click(metroFrame+i,48000,m.bpm,m.multiplier,m.beats,m.clickSound,m.accent)*m.clickVolume
                            if(m.clickRoute!="RIGHT")output[i*2]+=v
                            if(m.clickRoute!="LEFT")output[i*2+1]+=v
                        }
                        metroFrame+=512
                    } else metroFrame=0
                    if(pairReady && percussion.enabled && !percussion.follow && !state.preparing && loop!=null) { PercussionSynth.add(loop.audio,output,512,percussionFrame,48000,percussion.volume);percussionFrame+=512 }
                    else percussionFrame=0
                    limiter.process(output,512,2,48000)
                    check(track.write(output,0,output.size,AudioTrack.WRITE_BLOCKING)==output.size)
                    renderedIndependentFrames+=512
                }
            } catch(e:Exception) {
                if(generation.get()==token)mutable.update { it.copy(error="Falha na saída do ritmo: ${e.message}",metronomeArmed=false,percussion=it.percussion.copy(enabled=false)) }
            } finally {
                runCatching { track?.stop() };track?.release()
                synchronized(this) { if(generation.get()==token)workerRunning=false }
            }
        }
    }
    private fun readPcm(file:File,frames:Int):FloatArray {
        require(frames in 1..48000*60 && file.length()==frames*8L)
        val result=FloatArray(frames*2);val bytes=ByteArray(32768);var pos=0
        file.inputStream().buffered().use { stream ->
            while(pos<result.size) { val count=minOf(bytes.size,(result.size-pos)*4);java.io.DataInputStream(stream).readFully(bytes,0,count)
                ByteBuffer.wrap(bytes,0,count).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(result,pos,count/4);pos+=count/4 }
        }
        return result
    }
    private fun writePcm(file:File,data:FloatArray) {
        val atomic=AtomicFile(file);val stream=atomic.startWrite();val bytes=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN)
        try { var pos=0;while(pos<data.size) { val count=minOf(8192,data.size-pos);bytes.clear();bytes.asFloatBuffer().put(data,pos,count);stream.write(bytes.array(),0,count*4);pos+=count };atomic.finishWrite(stream) }
        catch(e:Exception) { atomic.failWrite(stream);throw e }
    }
}

object PercussionLoopStretch {
    suspend fun render(source:FloatArray,targetFrames:Int):FloatArray {
        require(source.isNotEmpty() && source.size%2==0)
        require(targetFrames in 1..48000*120) { "O loop ajustado deve ter até 120 segundos. Reduza os compassos ou aumente o BPM." }
        val ratio=(source.size/2).toDouble()/targetFrames
        require(ratio in .125..8.0) { "Ajuste o BPM ou os compassos do loop: a velocidade deve ficar entre 0,125× e 8× a original." }
        val result=if(abs(ratio-1)<1e-9)source.copyOf() else {
            val handle=NativePitch.createForSpeed(48000,0,ratio)
            try {
                val latency=NativePitch.stretchLatency(handle,ratio);val input=FloatArray(4096);val output=FloatArray((ceil(2048/ratio).toInt()+8)*2)
                val result=FloatArray(targetFrames*2);var pos=0L;var produced=0L;var written=0
                while(written<targetFrames) {
                    currentCoroutineContext().ensureActive();input.fill(0f)
                    val available=minOf(2048L,(source.size/2-pos).coerceAtLeast(0)).toInt()
                    if(available>0)source.copyInto(input,0,(pos*2).toInt(),(pos*2).toInt()+available*2)
                    val next=round((pos+2048)/ratio).toLong();val count=(next-produced).toInt()
                    NativePitch.stretch(handle,input,output,2048,count)
                    val skip=(latency-produced).coerceIn(0,count.toLong()).toInt();val take=minOf(count-skip,targetFrames-written)
                    output.copyInto(result,written*2,skip*2,(skip+take)*2);written+=take;produced=next;pos+=2048
                }
                result
            } finally { NativePitch.destroy(handle) }
        }
        // Short edge ramps prevent discontinuities in user-supplied loop boundaries.
        val fade=minOf(240,targetFrames/4)
        repeat(fade) { i -> val gain=i.toFloat()/fade;repeat(2) { c -> result[i*2+c]*=gain;result[(targetFrames-1-i)*2+c]*=gain } }
        for(i in result.indices)result[i]=(result[i].takeIf { it.isFinite() } ?: 0f).coerceIn(-1f,1f)
        return result
    }
}
