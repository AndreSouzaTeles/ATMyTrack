package com.atmytrack.app.audio

import kotlin.math.*

/** Sustained stereo triad with soft harmonics, detuned voices and a low-pass filter. */
class PadSynth(private val rate:Int=48000) {
    companion object {
        private const val TABLE=16384
        val sounds=listOf("Warm" to "Warm · suave", "Air" to "Air · ambiente", "Strings" to "Strings · cordas", "Organ" to "Organ · órgão")
        private val waves=arrayOf(doubleArrayOf(1.0,.22,.08),doubleArrayOf(1.0,.06,.18,.0,.08),doubleArrayOf(1.0,.4,.22,.12,.07),doubleArrayOf(1.0,.35,.0,.2,.0,.1)).map { harmonics ->
            DoubleArray(TABLE+1) { i -> val x=i.toDouble()/TABLE*2*PI;harmonics.mapIndexed { h,a -> a*sin((h+1)*x) }.sum()*.055 }
        }
    }
    private fun level(sample:Double):Float {
        val x=(sample*3.5).coerceIn(-3.0,3.0);val square=x*x
        return (.65*x*(27+square)/(27+9*square)).toFloat()
    }
    private val phase=DoubleArray(9)
    private val increments=DoubleArray(9)
    private var gain=0.0
    private var left=0.0;private var right=0.0
    fun fill(out:FloatArray,frames:Int,note:Int,minor:Boolean,volume:Float,sound:String="Warm") {
        val wave=waves[sounds.indexOfFirst { it.first==sound }.coerceAtLeast(0)]
        val cutoff=when(sound){"Air"->.09;"Strings"->.22;"Organ"->.28;else->.16}
        val notes=intArrayOf(48+note,48+note+if(minor)3 else 4,48+note+7)
        val target=DoubleArray(9) { i -> 440.0*2.0.pow((notes[i/3]-69+(i%3-1)*.065)/12)/rate }
        for(i in 0 until frames) {
            gain+=(volume-gain)*.0015
            var l=0.0;var r=0.0
            for(v in 0..8) {
                if(increments[v]==0.0)increments[v]=target[v]
                increments[v]+=(target[v]-increments[v])*.002
                phase[v]+=increments[v];if(phase[v]>=1.0)phase[v]-=1.0
                val x=phase[v]*TABLE;val index=x.toInt();val sample=wave[index]+(wave[index+1]-wave[index])*(x-index)
                l+=sample*(if(v%3==2).7 else 1.0);r+=sample*(if(v%3==0).7 else 1.0)
            }
            left+=(l-left)*cutoff;right+=(r-right)*cutoff
            out[i*2]=level(left)*gain.toFloat();out[i*2+1]=level(right)*gain.toFloat()
        }
    }
}
