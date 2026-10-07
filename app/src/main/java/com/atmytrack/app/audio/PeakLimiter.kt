package com.atmytrack.app.audio

import kotlin.math.*

/** Linked output protection: immediate attack, 80 ms exponential release.
 * Meters retain pre-limiter peaks so excessive gain is visible to the operator. */
class PeakLimiter {
    private var gain=1f
    fun reset() { gain=1f }
    fun process(samples:FloatArray,frames:Int,channels:Int,rate:Int):Boolean {
        val release=exp(-1.0/(rate*.08)).toFloat()
        var limiting=false
        repeat(frames) { frame ->
            var peak=0f
            repeat(channels) { peak=max(peak,abs(samples[frame*channels+it])) }
            val target=if(peak>.98f).98f/peak else 1f
            gain=min(target,1f-(1f-gain)*release)
            if(gain<.999f)limiting=true
            repeat(channels) { samples[frame*channels+it]*=gain }
        }
        return limiting
    }
}
