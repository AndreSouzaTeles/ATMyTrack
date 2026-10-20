package com.atmytrack.app.audio

import android.content.Context
import android.media.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class PadState(val note:Int=0,val minor:Boolean=false,val volume:Float=.25f,val playing:Boolean=false,val error:String?=null,val sound:String="Warm",val transitionFrames:Int=2048)
class PadEngine(private val context:Context) {
    private val prefs=context.getSharedPreferences("pad",0)
    private val mutable=MutableStateFlow(PadState(prefs.getInt("note",0),prefs.getBoolean("minor",false),prefs.getFloat("volume",.25f),sound=prefs.getString("sound","Warm") ?: "Warm"))
    val state=mutable.asStateFlow()
    private val generation=AtomicInteger()
    private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"ATMyTrack-PAD") }
    fun configure(note:Int=state.value.note,minor:Boolean=state.value.minor,volume:Float=state.value.volume,sound:String=state.value.sound,transitionFrames:Int=2048) {
        mutable.value=state.value.copy(note=note.coerceIn(0,11),minor=minor,volume=volume.coerceIn(0f,1f),transitionFrames=transitionFrames,sound=sound.takeIf { value->PadSynth.sounds.any { it.first==value } } ?: "Warm")
        prefs.edit().putString("sound",state.value.sound).putInt("note",state.value.note).putBoolean("minor",minor).putFloat("volume",state.value.volume).apply()
    }
    fun stop() { generation.incrementAndGet();mutable.value=state.value.copy(playing=false) }
    fun play() {
        if(state.value.playing)return
        val token=generation.incrementAndGet();mutable.value=state.value.copy(playing=true,error=null)
        worker.execute {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var track:AudioTrack?=null
            try {
                val rate=48000;val size=maxOf(AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT),16384)
                track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()).setBufferSizeInBytes(size).build()
                check(track.state==AudioTrack.STATE_INITIALIZED)
                val synth=PadCrossfade(rate);val block=FloatArray(1024);track.play()
                while(generation.get()==token) {
                    val settings=state.value;synth.target(PadTone(settings.note,settings.minor,settings.sound),settings.transitionFrames);synth.fill(block,512,settings.volume)
                    check(track.write(block,0,block.size,AudioTrack.WRITE_BLOCKING)==block.size)
                }
                repeat(12) { val settings=state.value;synth.fill(block,512,0f);track.write(block,0,block.size,AudioTrack.WRITE_BLOCKING) }
            } catch(e:Exception) {
                android.util.Log.e("ATMyTrack-PAD","Falha na saída do PAD",e)
                if(generation.get()==token)mutable.value=state.value.copy(playing=false,error="Não foi possível reproduzir o PAD. Confira a saída de áudio.")
            } finally { runCatching { track?.stop() };track?.release() }
        }
    }
}
