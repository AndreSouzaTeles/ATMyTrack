package com.atmytrack.app.tuner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TunerState(val reading:PitchReading=PitchReading(),val active:Boolean=false,val input:String="Microfone do sistema",val error:String?=null)
/** One serialized capture worker. No recording, network or playback effects. */
class TunerCapture(private val context:Context) {
    private val mutable=MutableStateFlow(TunerState())
    val state=mutable.asStateFlow()
    suspend fun capture()=withContext(captureDispatcher) { captureLock.withLock {
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return@withLock
        var record:AudioRecord?=null
        var counted=false
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val rate=48000
            val minimum=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            check(minimum>0)
            record=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum*2,16384)).build()
            check(record.state==AudioRecord.STATE_INITIALIZED)
            record.startRecording();check(record.recordingState==AudioRecord.RECORDSTATE_RECORDING)
            activeInstances.incrementAndGet();counted=true
            val chunk=ShortArray(1536);val window=FloatArray(4096);var filled=0
            val detector=YinDetector();val smoothing=PitchStabilizer()
            while(currentCoroutineContext().isActive) {
                // Non-blocking reads guarantee prompt lifecycle cancellation even on a removed USB input.
                val n=record.read(chunk,0,chunk.size,AudioRecord.READ_NON_BLOCKING)
                if(n<0)error("AudioRecord read $n")
                if(n==0) { delay(8);continue }
                // Continuous two-sample decimation, carrying incomplete groups between reads.
                for(i in 0 until n) {
                    decimationSum+=chunk[i]/32768f;decimationCount++
                    if(decimationCount==2) {
                        window[filled++]=decimationSum/2;decimationSum=0f;decimationCount=0
                        if(filled==window.size) {
                            val reading=smoothing.update(detector.detect(window))
                            mutable.value=TunerState(reading,true,record.routedDevice?.productName?.toString() ?: "Microfone do sistema")
                            window.copyInto(window,0,768,window.size);filled-=768
                        }
                    }
                }
            }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { Log.e("ATMyTrack-Tuner","Capture failed",e);mutable.value=TunerState(error="Não foi possível acessar a entrada de áudio. Confira o microfone e tente novamente.") }
        finally {
            record?.let { if(it.recordingState==AudioRecord.RECORDSTATE_RECORDING) { runCatching { it.stop() } };it.release() }
            if(counted)activeInstances.decrementAndGet()
            decimationCount=0;decimationSum=0f
            mutable.update { it.copy(active=false,reading=PitchReading()) }
        }
    }
    }
    private var decimationSum=0f
    private var decimationCount=0
    companion object {
        private val captureDispatcher=java.util.concurrent.Executors.newSingleThreadExecutor { r->Thread(r,"ATMyTrack-Tuner").apply { isDaemon=true } }.asCoroutineDispatcher()
        private val captureLock=Mutex(); val activeInstances=java.util.concurrent.atomic.AtomicInteger() }
}
