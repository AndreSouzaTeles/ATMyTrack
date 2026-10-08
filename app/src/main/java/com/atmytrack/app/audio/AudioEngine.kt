package com.atmytrack.app.audio

import android.media.*
import android.content.Context
import android.os.Process
import com.atmytrack.app.data.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import kotlinx.coroutines.*

data class PreparationItem(val id:String,val name:String,val status:String,val detail:String="")

data class PlaybackState(val projectId: String = "", val playing: Boolean = false,
    val frame: Long = 0, val peaks: List<Float> = emptyList(), val left: Float = 0f,
    val right: Float = 0f, val error: String? = null, val underruns: Int = 0,
    val busPeaks: Map<String,Float> = emptyMap(), val outputName: String = "Saída Android", val outputChannels: Int = 2, val preparing:Boolean=false, val ready:Boolean=false,
    val preparation:List<PreparationItem> = emptyList(), val preparationMs:Long=0, val starvation:Long=0, val limiting:Boolean=false,val producerWaits:Long=0)

class AudioEngine(private val context: Context) {
    @Volatile var maxReadNanos=0L; private set
    @Volatile var clippedBlocks=0L; private set
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private val commands = LinkedBlockingQueue<() -> Unit>()
    @Volatile private var project: Project? = null
    private var readers = emptyMap<String,FrameReader>()
    private var busBuffers = emptyMap<String,FloatArray>()
    private val busLevels = mutableMapOf<String,Float>()
    private var channelGains = emptyMap<String,Float>()
    private var peakBuffer = FloatArray(0)
    private val preparers=Executors.newFixedThreadPool(2) { r -> Thread(r,"ATMyTrack-Prepare").apply { isDaemon=true } }
    private val cleanup = Executors.newSingleThreadExecutor { r -> Thread(r,"ATMyTrack-Cleanup").apply { isDaemon=true } }
    private var sink: AudioTrack? = null
    private var pump:OutputPump?=null
    private val generation=java.util.concurrent.atomic.AtomicInteger()
    private var priming=false
    private val smooth=mutableMapOf<String,FloatArray>()
    private val limiter=PeakLimiter()
    private var limited=false
    private var masterGain=1f
    private var masterPan=0f
    private val busGains=mutableMapOf<String,Float>()
    private var timelineScale = 1.0
    private var fadeIn = 0
    private var cursor = 0L
    private var endFrame=0L
    private var base = 0L
    private var headBase = 0L
    private var playing = false
    private var ending = false
    private val input = FloatArray(1024)
    private val output = FloatArray(1024)
    private var hardware = FloatArray(1024)
    private var outputChannels=2
    private var outputName="Saída Android"
    private var deviceId: Int? = null
    private var outputUnavailable = false
    private var lastPublish = 0L

    init {
        context.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(devices:Array<out AudioDeviceInfo>) {
                commands.offer {
                    if(devices.any { it.id == deviceId }) {
                        val position=audiblePosition(); playing=false; reset(position)
                        pump?.close();pump=null;sink=null; outputUnavailable=true; outputChannels=2
                        mutable.value=mutable.value.copy(playing=false,frame=kotlin.math.round(position*timelineScale).toLong(),outputChannels=2,outputName="Interface desconectada",error="Interface desconectada. Use ATUALIZAR SAÍDA e revise os destinos dos buses antes de retomar.")
                    }
                }
            }
        },android.os.Handler(android.os.Looper.getMainLooper()))
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            while (true) {
                try {
                    if (!playing) commands.take().invoke()
                    while (true) { val c = commands.poll() ?: break; c() }
                    if (playing) render()
                } catch (e: Exception) {
                    playing = false
                    runCatching { pump?.reset() }
                    mutable.value = mutable.value.copy(playing = false, preparing=false,ready=false, error = "Áudio interrompido: ${e.message ?: "falha na saída ou arquivo indisponível"}")
                }
            }
        }, "ATMyTrack-Mixer").apply { isDaemon = true; start() }
    }
    fun cancelPreparation() {
        generation.incrementAndGet()
        if(mutable.value.preparing) {
            val pending=mutable.value.preparation.firstOrNull { it.status!="READY" }?.id
            mutable.value=mutable.value.copy(preparing=false,ready=false,preparation=mutable.value.preparation.map { if(it.id==pending)it.copy(status="ERROR",detail="Preparação cancelada") else it })
        }
    }
    fun load(source: Project, preserve: Boolean = false) {
        val p=source.engineView()
        val ticket=generation.incrementAndGet()
        mutable.value=mutable.value.copy(ready=false,preparing=true,error=null,preparation=p.stems.map { PreparationItem(it.id,it.name,"DISCOVERED") })
        commands.offer {
        if(ticket!=generation.get())return@offer
        val started=android.os.SystemClock.elapsedRealtime()
        fun checkCancelled() { check(ticket==generation.get()) { "Preparação cancelada. Toque em TENTAR NOVAMENTE para continuar." } }
        fun item(id:String,status:String,detail:String="") { if(ticket==generation.get())mutable.update { state -> state.copy(preparation=state.preparation.map { if(it.id==id)it.copy(status=status,detail=detail) else it }) } }

        maxReadNanos=0;clippedBlocks=0
        val resume=preserve && playing
        val position=if(preserve)(audiblePosition()*timelineScale/source.timelineScale).toLong().coerceAtMost(p.frames) else 0L
        playing = false; pump?.close();pump=null; sink = null
        readers.values.forEach { runCatching { it.close() } }; readers = emptyMap(); project = null
        val opened = mutableMapOf<String,FrameReader>()
        try {
            val pending=p.stems.map { s -> s to preparers.submit<FrameReader> {
                var reader:FrameReader?=null
                try {
                    checkCancelled();item(s.id,"PREPARING")
                    reader=ReadAheadReader(PlaybackCache(context).open(s,p.sampleRate,::checkCancelled) { percent -> item(s.id,"PREPARING","$percent%") })
                    reader.read(position,512,FloatArray(1024))
                    item(s.id,"READY")
                    reader
                } catch(e:Exception) { reader?.close();item(s.id,"ERROR",e.message ?: "Falha no preparo");throw e }
            } }
            var failure:Throwable?=null
            pending.forEach { (s,future) ->
                try { opened[s.id]=future.get() } catch(e:Exception) { if(failure==null)failure=e.cause ?: e }
            }
            failure?.let { throw IllegalStateException(it.message,it) }
            checkCancelled()
            val device=context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type==AudioDeviceInfo.TYPE_USB_DEVICE || it.type==AudioDeviceInfo.TYPE_USB_HEADSET }
            outputChannels=device?.channelCounts?.filter { it in 2..32 }?.maxOrNull() ?: 2
            outputName=device?.productName?.toString() ?: "Saída padrão Android"
            deviceId=device?.id
            val min = AudioTrack.getMinBufferSize(p.sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            require(min > 0) { "Taxa de amostragem não suportada pela saída." }
            fun create(channels:Int) = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(p.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).apply {
                    if(channels==2)setChannelMask(AudioFormat.CHANNEL_OUT_STEREO) else setChannelIndexMask(if(channels==32)-1 else (1 shl channels)-1)
                }.build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(min * 2, 4096 * channels * 4)).build()
            val track=try { create(outputChannels) } catch(_:Exception) { outputChannels=2; create(2) }
            if(track.state != AudioTrack.STATE_INITIALIZED) { track.release();error("Não foi possível abrir a saída estéreo.") }
            if(device!=null && !track.setPreferredDevice(device)) { track.release(); error("Não foi possível selecionar a interface USB.") }
            hardware=FloatArray(512*outputChannels)
            sink = track;pump=OutputPump(track,outputChannels,Thread.currentThread()); readers = opened; project = p; timelineScale=source.timelineScale; outputUnavailable=false
            busBuffers=p.buses.associate { it.id to FloatArray(1024) }
            configureGains(p)
            cursor = position;endFrame=if(p.loop)Long.MAX_VALUE else p.frames; base = position; headBase = 0; ending = false
            smooth.clear();busGains.clear();masterGain=if(p.masterMute)0f else p.master;masterPan=p.masterPan;limiter.reset()
            mutable.value=mutable.value.copy(preparing=false,ready=true,preparationMs=android.os.SystemClock.elapsedRealtime()-started)
            if(resume) { primeReaders(position); pump?.start(); playing=true }
            publish(force = true)
        } catch (e: Exception) {
            opened.values.forEach { runCatching { it.close() } }
            pump?.close();pump=null;sink=null;readers=emptyMap();project=null
            if(ticket==generation.get())throw e
        }
    } }
    fun update(source: Project) { commands.offer { if (source.id == project?.id) {
        val live=project!!
        // Mixer edits may arrive while a prepared DSP swap is committing. They must
        // never restore old cache metadata or temporal coordinates from the UI snapshot.
        val p=source.engineView().copy(semitones=live.semitones,targetKey=live.targetKey,pitchTracks=live.pitchTracks,
            bpm=source.bpm*timelineScale,beatOffset=kotlin.math.round(source.beatOffset/timelineScale).toLong(),
            markers=source.markers.map { it.copy(start=kotlin.math.round(it.start/timelineScale).toLong(),end=kotlin.math.round(it.end/timelineScale).toLong()) },
            stems=source.stems.map { s -> live.stems.find { it.id==s.id }?.let { current ->
                s.copy(frames=current.frames,pitchFile=current.pitchFile,pitchApplied=current.pitchApplied,dspSpeed=100)
            } ?: s })
        val reposition=project?.loopSection!=p.loopSection
        if(!reposition && project?.loop!=p.loop) {
            endFrame=if(p.loop)Long.MAX_VALUE else (absolutePosition()/p.frames.coerceAtLeast(1)+1)*p.frames
            ending=!p.loop && cursor>=endFrame
        }
        val position=audiblePosition()
        project = p; busBuffers=p.buses.associate { it.id to (busBuffers[it.id] ?: FloatArray(1024)) }; configureGains(p)
        if(reposition) { val resume=playing;reset(position);if(resume) { primeReaders(cursor);pump?.start() };publish(force=true) }

    } } }
    private fun configureGains(p:Project) {
        channelGains=p.stems.associate { it.id to it.volume*ConsoleMath.dcaGain(it,p) }
        if(peakBuffer.size!=p.stems.size)peakBuffer=FloatArray(p.stems.size)
        busLevels.keys.retainAll(p.buses.map { it.id }.toSet())
    }
    /** Prepare only changed readers off the mixer, then swap at a shared block boundary.
     * AudioTrack, queued samples, playback head and the transport cursor are untouched. */
    suspend fun applyPreparedPitch(source:Project) = withContext(NonCancellable) {
        val p=source.engineView()
        val before=project ?: return@withContext
        if(before.id!=p.id)return@withContext
        val prepared=withContext(Dispatchers.IO) {
            val opened=mutableMapOf<String,FrameReader>()
            try {
                p.stems.forEach { s -> if(before.stems.find { it.id==s.id }?.pitchFile!=s.pitchFile) {
                    opened[s.id]=ReadAheadReader(PlaybackCache(context).open(s,p.sampleRate))
                } }
                opened
            } catch(e:Exception) { opened.values.forEach { runCatching { it.close() } };throw e }
        }
        val committed=CompletableDeferred<Unit>()
        commands.offer {
            try {
                val current=project
                if(current?.id==p.id) {
                    check(current.stems.all { s -> p.stems.any { it.id==s.id && it.fingerprint==s.fingerprint } }) { "A origem mudou durante o preparo do pitch. Aplique novamente." }
                    val changingTime=timelineScale!=source.timelineScale || current.frames!=p.frames || current.stems.zip(p.stems).any { it.first.frames!=it.second.frames }
                    val resume=playing
                    val originalPosition=audiblePosition()*timelineScale
                    if(changingTime) { pump?.reset();playing=false }
                    val retired=prepared.keys.mapNotNull { readers[it] }
                    readers=readers+prepared
                    project=current.copy(key=p.key,targetKey=p.targetKey,semitones=p.semitones,pitchTracks=p.pitchTracks,bpm=p.bpm,beatOffset=p.beatOffset,markers=p.markers,
                        stems=current.stems.map { s -> val next=p.stems.first { it.id==s.id };s.copy(pitchFile=next.pitchFile,pitchApplied=next.pitchApplied,frames=next.frames) })
                    timelineScale=source.timelineScale
                    if(changingTime) {
                        reset((originalPosition/timelineScale).toLong().coerceIn(0,p.frames))
                        fadeIn=256
                        if(resume) { primeReaders(cursor);pump?.start();playing=true }
                    }
                    publish(force=true)
                    cleanup.execute { retired.forEach { runCatching { it.close() } } }
                } else cleanup.execute { prepared.values.forEach { runCatching { it.close() } } }
                committed.complete(Unit)
            } catch(e:Exception) { cleanup.execute { prepared.values.forEach { runCatching { it.close() } } };committed.completeExceptionally(e) }
        }
        // Once enqueued, the mixer owns the readers even if the caller's scope is cancelled.
        committed.await()
    }
    fun play() { commands.offer {
        val p = project ?: return@offer
        check(mutable.value.ready && !mutable.value.preparing) { "Aguarde a preparação de todas as tracks." }
        check(!outputUnavailable) { "Atualize a saída e revise o routing antes de reproduzir." }
        if (playing) return@offer
        if (cursor >= p.frames) reset(0)
        p.loopSection?.let { if(cursor !in it.start until it.end)reset(it.start) }
        primeReaders(cursor)
        pump!!.start(); playing = true; ending = false; publish(force = true)
    } }
    fun pause(message: String? = null) { commands.offer {
        val position = audiblePosition()
        reset(position); playing = false
        publish(force = true)
        if (message != null) mutable.value = mutable.value.copy(error = message)
    } }
    fun stop() { commands.offer { playing = false; reset(0); publish(force = true) } }
    fun seek(frame: Long) { commands.offer {
        val resume = playing
        reset((frame/timelineScale).toLong().coerceIn(0, project?.frames ?: 0))
        if (resume) { primeReaders(cursor); pump?.start() }
        publish(force = true)
    } }
    fun unload() { cancelPreparation();commands.offer {
        playing = false; pump?.close();pump=null; sink = null
        readers.values.forEach { runCatching { it.close() } }; readers = emptyMap(); project = null
        mutable.value = PlaybackState()
    } }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    private fun reset(frame: Long) {
        pump?.reset();limiter.reset()
        val bounded=project?.loopSection?.let { frame.coerceIn(it.start,it.end-1) } ?: frame
        cursor = bounded;endFrame=if(project?.loop==true)Long.MAX_VALUE else project?.frames ?: 0; base = bounded; headBase = sink?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0
        ending = false
    }
    private fun primeReaders(frame:Long) {
        // Fill the shared ring while the hardware clock is stopped. Disk seeks
        // happen on this producer, never on ATMyTrack-Output.
        cursor=frame
        priming=true
        try {
            val queue=pump ?: return
            while(true) {
                repeat(8) { if(cursor<endFrame && !queue.full)render() }
                if(queue.prefill()==0 || cursor>=endFrame)break
            }
        } finally { priming=false }
    }
    private fun absolutePosition():Long {
        val head=sink?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0
        return (base+((head-headBase) and 0xffffffffL)).coerceIn(base,cursor)
    }
    private fun audiblePosition(): Long {
        if(!playing)return cursor
        val absolute=absolutePosition();val total=project?.frames ?: 0
        if(total<=0)return 0
        return if(absolute>=endFrame)total else project?.playbackFrame(absolute) ?: 0
    }
    private fun render() {
        val p = project ?: return
        if(sink==null)return
        val queue=pump ?: return
        queue.error?.let { error(it) }
        if (ending) {
            if (absolutePosition() >= endFrame) {
                reset(p.frames); playing = false; publish(force = true)
            } else { publish(); Thread.sleep(4) }
            return
        }
        if(queue.full) { java.util.concurrent.locks.LockSupport.parkNanos(5_000_000);return }
        val renderFrame=p.playbackFrame(cursor)
        val count = minOf(512L, (p.loopSection?.end ?: p.frames)-renderFrame,endFrame-cursor).toInt()
        if (count <= 0) { ending = true; return }
        output.fill(0f)
        hardware.fill(0f); busBuffers.values.forEach { it.fill(0f) }
        val anySolo = p.stems.any { it.solo }
        peakBuffer.fill(0f)
        p.stems.forEachIndexed { i, s ->
            val active = !s.mute && (!anySolo || s.solo)
            var peak = 0f
            val reader=readers.getValue(s.id)
            val start=System.nanoTime();reader.read(renderFrame,count,input);maxReadNanos=maxOf(maxReadNanos,System.nanoTime()-start)
            val gain=if(active)channelGains.getValue(s.id) else 0f
            val left=gain*(1f-kotlin.math.max(0f,s.pan));val right=gain*(1f+kotlin.math.min(0f,s.pan))
            val previous=smooth.getOrPut(s.id) { floatArrayOf(left,right) }
            val destination=busBuffers[s.bus] ?: output
            repeat(count) { j ->
                val t=(j+1f)/count
                val l=previous[0]+(left-previous[0])*t;val r=previous[1]+(right-previous[1])*t
                val a=input[j*2]*l;val b=input[j*2+1]*r
                when(s.route) { "LEFT"->destination[j*2]+=(a+b)*.5f;"RIGHT"->destination[j*2+1]+=(a+b)*.5f;else->{destination[j*2]+=a;destination[j*2+1]+=b} }
                peak=maxOf(peak,kotlin.math.abs(a),kotlin.math.abs(b))
            }
            previous[0]=left;previous[1]=right
            peakBuffer[i] = peak
        }
        p.buses.forEach { bus ->
            val buffer=busBuffers.getValue(bus.id); val gain=if(bus.mute)0f else bus.volume
            val previous=busGains[bus.id] ?: gain
            var level=0f
            repeat(count) { frame ->
                val g=previous+(gain-previous)*(frame+1f)/count
                buffer[frame*2]*=g;buffer[frame*2+1]*=g
                level=maxOf(level,kotlin.math.abs(buffer[frame*2]),kotlin.math.abs(buffer[frame*2+1]))
            }
            busGains[bus.id]=gain
            if(bus.destination.startsWith("MONO:")) {
                ConsoleMath.routePhysical(buffer,hardware,count,outputChannels,bus.destination)
            } else if(bus.destination.startsWith("OUT:")) {
                val pair=bus.destination.substringAfter(':').toIntOrNull() ?: 0
                check(pair>=0 && pair%2==0 && pair+1<outputChannels) { "Destino físico indisponível: ${bus.name}. Revise o routing." }
                repeat(count) { hardware[it*outputChannels+pair]+=buffer[it*2]; hardware[it*outputChannels+pair+1]+=buffer[it*2+1] }
            } else ConsoleMath.route(buffer,output,count,1f,0f,bus.destination)
            busLevels[bus.id]=level
        }
        val levels = MixMath.finish(output, count, renderFrame, p, clamp=false,fromGain=masterGain,fromPan=masterPan)
        masterGain=if(p.masterMute)0f else p.master;masterPan=p.masterPan
        if(levels.first>1f || levels.second>1f)clippedBlocks++
        repeat(count) { hardware[it*outputChannels]+=output[it*2]; hardware[it*outputChannels+1]+=output[it*2+1] }
        limited=limiter.process(hardware,count,outputChannels,p.sampleRate)
        if(fadeIn>0) repeat(count) { f ->
            val gain=if(fadeIn>0)1f-(fadeIn--)/256f else 1f
            repeat(outputChannels) { ch -> hardware[f*outputChannels+ch]*=gain }
        }
        check(queue.offer(hardware,count))
        cursor += count
        publish(peakBuffer, levels.first, levels.second)
    }
    private fun publish(peaks: FloatArray? = null, left: Float = 0f, right: Float = 0f, force: Boolean = false) {
        if(priming)return
        val now = System.nanoTime()
        if (!force && now - lastPublish < 33_000_000) return
        lastPublish = now
        mutable.value = PlaybackState(project?.id ?: "", playing, kotlin.math.round(audiblePosition()*timelineScale).toLong(), peaks?.toList() ?: emptyList(), left, right, underruns = sink?.underrunCount ?: 0,busPeaks=busLevels.toMap(),outputName=outputName,outputChannels=outputChannels,ready=mutable.value.ready,preparing=mutable.value.preparing,preparation=mutable.value.preparation,preparationMs=mutable.value.preparationMs,starvation=pump?.starvation ?: 0,limiting=limited,producerWaits=pump?.producerWaits ?: 0)
    }
}
