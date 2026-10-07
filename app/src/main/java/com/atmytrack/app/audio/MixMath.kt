package com.atmytrack.app.audio

import com.atmytrack.app.data.Project
import com.atmytrack.app.data.MusicTime
import kotlin.math.*

object MixMath {
    // Balance law preserves a stereo stem at center and attenuates the opposite side.
    fun gains(volume: Float, pan: Float): Pair<Float, Float> =
        volume * (1f - max(0f, pan)) to volume * (1f + min(0f, pan))
    fun add(input: FloatArray, output: FloatArray, frames: Int, volume: Float, pan: Float) {
        val (left, right) = gains(volume, pan)
        for (i in 0 until frames) { output[i * 2] += input[i * 2] * left; output[i * 2 + 1] += input[i * 2 + 1] * right }
    }
    fun click(frame: Long, rate: Int, bpm: Double, multiplier: Double, beats: Int, sound:String="Classic", accent:Boolean=true, offset:Long=0): Float {
        if(frame<offset)return 0f
        val period = MusicTime.beatFrames(rate, bpm, multiplier)
        val beat = floor((frame-offset) / period).toLong()
        val local = frame-offset - beat * period
        val duration = rate * 0.022
        if (local >= duration) return 0f
        val emphasis=accent && beat%beats==0L
        val hz=when(sound) { "Digital"->2200.0; "Wood"->650.0; "Cowbell"->540.0; "Soft"->750.0; "High Tick"->3200.0; "Low Tick"->400.0; else->1100.0 } * if(emphasis)1.45 else 1.0
        val phase=2*PI*hz*local/rate
        val wave=when(sound) { "Digital"->tanh(3*sin(phase)); "Wood"->sin(phase)+.4*sin(phase*2.76); "Cowbell"->.5*(sin(phase)+sin(phase*1.48)); else->sin(phase) }
        val decay=if(sound=="Soft") .0025 else if(sound=="Cowbell") .009 else .005
        return (wave*exp(-local/(rate*decay))*(if(emphasis)1.0 else .7)).toFloat()
    }
    fun finish(output: FloatArray, frames: Int, start: Long, p: Project, clamp:Boolean=true,fromGain:Float=if(p.masterMute)0f else p.master): Pair<Float, Float> {
        var l = 0f; var r = 0f
        val target = if (p.masterMute) 0f else p.master
        for (i in 0 until frames) {
            val gain=fromGain+(target-fromGain)*(i+1f)/frames
            val click = if (p.click) click(start + i, p.sampleRate, p.bpm, p.multiplier, p.beats,p.clickSound,p.accent,p.beatOffset) * p.clickVolume else 0f
            val a = (output[2*i] + if(p.clickRoute=="RIGHT")0f else click) * gain; val b = (output[2*i+1] + if(p.clickRoute=="LEFT")0f else click) * gain
            l = max(l, abs(a)); r = max(r, abs(b))
            output[2*i] = if(clamp)a.coerceIn(-1f, 1f) else a; output[2*i+1] = if(clamp)b.coerceIn(-1f, 1f) else b
        }
        return l to r
    }
}
