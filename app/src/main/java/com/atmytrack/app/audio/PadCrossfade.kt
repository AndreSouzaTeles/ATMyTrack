package com.atmytrack.app.audio

data class PadTone(val note:Int,val minor:Boolean,val sound:String)

/** Sample-counted chord crossfade. Retargeting retains the audible mixture, never glides pitches. */
class PadCrossfade(private val rate:Int=48000) {
    private data class Voice(val tone:PadTone,val synth:PadSynth,var from:Float,var gain:Float,var target:Float)
    private val voices=mutableListOf<Voice>()
    private var tone:PadTone?=null
    private var duration=1
    var remainingFrames=0; private set
    private var scratch=FloatArray(0)
    fun target(next:PadTone,frames:Int=rate*3) {
        if(next==tone)return
        tone=next
        voices.removeAll { it.gain<.00001f }
        if(voices.isEmpty()) { voices+=Voice(next,PadSynth(rate),1f,1f,1f);remainingFrames=0;return }
        voices.forEach { it.from=it.gain;it.target=0f }
        val incoming=voices.firstOrNull { it.tone==next }
        if(incoming!=null)incoming.target=1f else voices+=Voice(next,PadSynth(rate),0f,0f,1f)
        duration=frames.coerceAtLeast(1);remainingFrames=duration
    }
    fun fill(out:FloatArray,frames:Int,volume:Float) {
        require(out.size>=frames*2)
        if(scratch.size<frames*2)scratch=FloatArray(frames*2)
        out.fill(0f,0,frames*2)
        val elapsed=duration-remainingFrames
        voices.forEach { voice ->
            voice.synth.fill(scratch,frames,voice.tone.note,voice.tone.minor,volume,voice.tone.sound)
            for(i in 0 until frames) {
                val progress=if(remainingFrames==0)1f else ((elapsed+i+1).toFloat()/duration).coerceAtMost(1f)
                val gain=voice.from+(voice.target-voice.from)*progress
                out[i*2]+=scratch[i*2]*gain;out[i*2+1]+=scratch[i*2+1]*gain
            }
            val progress=if(remainingFrames==0)1f else ((elapsed+frames).toFloat()/duration).coerceAtMost(1f)
            voice.gain=voice.from+(voice.target-voice.from)*progress
        }
        remainingFrames=(remainingFrames-frames).coerceAtLeast(0)
        if(remainingFrames==0)voices.removeAll { it.target==0f }
    }
}
