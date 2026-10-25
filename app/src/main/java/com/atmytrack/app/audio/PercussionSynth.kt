package com.atmytrack.app.audio

import kotlin.math.*

/** Four-bar grooves arranged from real CC0 acoustic recordings. */
object PercussionSynth {
    val patterns=listOf("Pop / Rock", "Rock 16", "Balada", "Disco", "Funk", "Samba", "Bossa nova", "Reggae", "Shuffle", "Afro / 6-8")
    private data class Hit(val step:Int,val voice:Int,val gain:Float=1f)
    fun barFrames(rate:Int,bpm:Double,beats:Int,denominator:Int):Int {
        require(bpm.isFinite() && bpm in 15.0..600.0 && beats in 1..12 && denominator in listOf(4,8))
        return (rate*60.0/bpm*beats*4/denominator).roundToInt()
    }
    fun render(pattern:String,bpm:Double,beats:Int,denominator:Int,rate:Int=48000,kit:AcousticKit):FloatArray {
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
            5->{add(0,0,6,8,14);add(4,2,5,10,13);add(5,0,3,7,8,11,15);add(6,*IntArray(16){it});add(7,0,4,8,12)}
            6->{add(0,0,6,8,14);add(4,4,12);add(2,0,2,4,6,8,10,12,14);add(5,3,10)}
            7->{add(0,0,8);add(1,8);add(3,2,6,10,14);add(4,4,12)}
            8->{add(0,0,6);add(1,3,9);add(2,0,2,3,5,6,8,9,11)}
            9->{add(0,0,6);add(5,2,4,8,10);add(4,3,9);add(6,*IntArray(12){it})}
        }
        require(rate==48000)
        val bar=barFrames(rate,bpm,beats,denominator);val frames=bar*4;val output=FloatArray(frames*2)
        val roles=listOf("kick","snare","hat","openhat","stick","conga","shaker","tamb","tom","crash","ride","ghost")
        for(cycle in 0 until 4) {
            val phrase=hits.toMutableList()
            if(index in listOf(0,1,3,4,8)) {
                if(cycle%2==1)phrase+=Hit(if(index>=8)10 else 11,if(index>=8)8 else 11,.42f)
                if(cycle==3){phrase+=Hit(steps-2,8,.62f);phrase+=Hit(steps-1,8,.76f)}
                if(cycle==0)phrase+=Hit(0,9,.42f)
            }
            for((hitIndex,hit) in phrase.withIndex()) {
                val role=roles[hit.voice];val variants=kit.samples.getValue(role)
                val sample=variants[(cycle+hitIndex+hit.step)%variants.size]
                val jitter=if(hit.step==0)0 else ((hitIndex*7+cycle*11+hit.voice*3)%7-3)*24
                val start=(cycle.toLong()*bar+hit.step.toLong()*bar/steps+jitter+frames)%frames
                val accent=if(hit.voice in listOf(2,6,7))if(hit.step%4==0)1f else if(hit.step%2==0).78f else .56f else 1f
                val level=hit.gain*accent*(.94f+((cycle*3+hitIndex)%5)*.015f)*.78f
                for(i in 0 until sample.size/2) {
                    val frame=((start+i)%frames).toInt()
                    output[frame*2]+=sample[i*2]*level;output[frame*2+1]+=sample[i*2+1]*level
                }
            }
        }
        val peak=output.maxOf { abs(it) }.coerceAtLeast(.90f)
        if(peak>.90f)for(i in output.indices)output[i]*=.90f/peak
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
