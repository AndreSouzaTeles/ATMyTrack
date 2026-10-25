package com.atmytrack.app.audio

import kotlin.math.*

/** Original synthesized grooves, no recordings or third-party sample library. */
object PercussionSynth {
    val patterns=listOf("Pop / Rock", "Rock 16", "Balada", "Disco", "Funk", "Samba", "Bossa nova", "Reggae", "Shuffle", "Afro / 6-8")
    private data class Hit(val step:Int,val voice:Int,val gain:Float=1f)
    fun barFrames(rate:Int,bpm:Double,beats:Int,denominator:Int):Int {
        require(bpm.isFinite() && bpm in 15.0..600.0 && beats in 1..12 && denominator in listOf(4,8))
        return (rate*60.0/bpm*beats*4/denominator).roundToInt()
    }
    fun render(pattern:String,bpm:Double,beats:Int,denominator:Int,rate:Int=48000):FloatArray {
        require(pattern in patterns)
        val index=patterns.indexOf(pattern);val steps=if(index>=8)12 else 16
        val hits=mutableListOf<Hit>()
        fun add(voice:Int,vararg positions:Int) { positions.forEach { hits+=Hit(it,voice) } }
        when(index) {
            0->{add(0,0,8);add(1,4,12);add(2,0,2,4,6,8,10,12,14)}
            1->{add(0,0,6,8,10);add(1,4,12);add(2,*IntArray(16){it})}
            2->{add(0,0,10);add(4,4,12);add(2,0,4,8,12)}
            3->{add(0,0,4,8,12);add(1,4,12);add(3,2,6,10,14)}
            4->{add(0,0,3,7,10);add(1,4,12);add(2,0,2,5,6,8,10,13,14);hits+=Hit(1,1,.3f)}
            5->{add(0,0,6,8,14);add(4,2,5,10,13);add(5,0,3,7,8,11,15);add(6,*IntArray(16){it})}
            6->{add(0,0,6,8,14);add(4,4,12);add(2,0,2,4,6,8,10,12,14);add(5,3,10)}
            7->{add(0,0,8);add(1,8);add(3,2,6,10,14);add(4,4,12)}
            8->{add(0,0,6);add(1,3,9);add(2,0,2,3,5,6,8,9,11)}
            9->{add(0,0,6);add(5,2,4,8,10);add(4,3,9);add(6,*IntArray(12){it})}
        }
        val frames=barFrames(rate,bpm,beats,denominator);val output=FloatArray(frames*2)
        for(hit in hits) {
            val start=hit.step.toLong()*frames/steps
            val length=(rate*when(hit.voice){0->.25;1->.16;3->.11;5->.18;else->.055}).toInt()
            repeat(length) { i ->
                val t=i.toDouble()/rate
                val hash=(i*1664525+hit.step*1013904223) xor (i shl 13)
                val noise=((hash ushr 8 and 65535)/32768.0-1)
                val sample=when(hit.voice) {
                    0->sin(2*PI*(48*t+8*(1-exp(-t*35))))*exp(-t*22)*.65
                    1->(noise*.7+sin(2*PI*185*t)*.3)*exp(-t*35)*.42
                    2->noise*exp(-t*100)*.12
                    3->noise*exp(-t*38)*.15
                    4->sin(2*PI*1800*t)*exp(-t*160)*.24
                    5->(sin(2*PI*190*t)+.25*sin(2*PI*340*t))*exp(-t*28)*.32
                    else->noise*exp(-t*140)*.09
                }
                val frame=((start+i)%frames).toInt();val v=sample.toFloat()*hit.gain
                output[frame*2]+=v;output[frame*2+1]+=v
            }
        }
        val peak=output.maxOf { abs(it) }.coerceAtLeast(.65f)
        if(peak>.65f)for(i in output.indices)output[i]*=.65f/peak
        return output
    }
    /** Sample from a pre-rendered loop at any timeline position; no I/O or decoder on playback. */
    fun add(loop:FloatArray,output:FloatArray,count:Int,start:Long,rate:Int,volume:Float) {
        val frames=loop.size/2;if(frames==0)return
        repeat(count) { i ->
            val position=(start+i)*48000.0/rate
            val a=(floor(position).toLong()%frames+frames).toInt()%frames;val b=(a+1)%frames;val fraction=(position-floor(position)).toFloat()
            repeat(2) { c -> output[i*2+c]+=(loop[a*2+c]+(loop[b*2+c]-loop[a*2+c])*fraction)*volume }
        }
    }
}
