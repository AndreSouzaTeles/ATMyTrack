package com.atmytrack.app.audio

import com.atmytrack.app.data.*
import kotlin.math.*

object ConsoleMath {
    val points = listOf(0f to -80f, .06f to -60f, .14f to -40f, .23f to -30f, .35f to -20f, .50f to -10f, .64f to -5f, .78f to 0f, .89f to 5f, 1f to 10f)
    fun db(gain: Float): Float = if (gain <= 0) -80f else (20 * log10(gain)).coerceIn(-80f,10f)
    fun gain(db: Float) = if (db <= -79.9f) 0f else 10f.pow(db / 20)
    private val inverse = points.map { it.second to it.first }
    fun position(gain: Float): Float = interpolate(db(gain), inverse)
    fun fromPosition(p: Float) = gain((interpolate(p.coerceIn(0f,1f),points)*10).roundToInt()/10f)
    private fun interpolate(x: Float, points: List<Pair<Float,Float>>): Float {
        val i = (1 until points.size).firstOrNull { x <= points[it].first } ?: points.lastIndex
        val a=points[i-1]; val b=points[i]
        return a.second+(b.second-a.second)*((x-a.first)/(b.first-a.first)).coerceIn(0f,1f)
    }
    fun dcaGain(s: Stem, p: Project): Float {
        var gain=1f
        for(d in p.dcas)if(s.id in d.members) { if(d.mute)return 0f; gain*=d.volume }
        return gain
    }
    fun route(input: FloatArray, output: FloatArray, count: Int, gain: Float, pan: Float, route: String) {
        val l=gain*(1f-max(0f,pan)); val r=gain*(1f+min(0f,pan))
        for (i in 0 until count) {
            val a=input[i*2]*l; val b=input[i*2+1]*r
            when(route) {
                "LEFT" -> output[i*2] += (a+b)*.5f
                "RIGHT" -> output[i*2+1] += (a+b)*.5f
                else -> { output[i*2]+=a; output[i*2+1]+=b }
            }
        }
    }
    private val names=listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    fun note(value: String): Int = names.indexOf(mapOf("Db" to "C#","Eb" to "D#","Gb" to "F#","Ab" to "G#","Bb" to "A#")[value] ?: value)
    fun transpose(origin: String, destination: String): Int { val d=note(destination)-note(origin); return if(d>6)d-12 else if(d< -6)d+12 else d }
    fun target(origin: String, semitones: Int): String = names[((note(origin).coerceAtLeast(0)+semitones)%12+12)%12]
}
