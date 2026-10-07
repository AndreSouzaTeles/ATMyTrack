package com.atmytrack.app.audio

import kotlin.math.*

/** Polyphase windowed-sinc conversion on preparation/analysis workers only.
 * Positions derive from the absolute output frame, so seeks cannot accumulate drift. */
class ResampledReader(private val source:FrameReader,sourceRate:Int,targetRate:Int,private val frames:Long):FrameReader {
    private val ratio=sourceRate.toDouble()/targetRate
    private val blocks=Array(2) { FloatArray(8192) }
    private val starts=longArrayOf(-1,-1)
    private val weights=Array(1024) { phase ->
        val cutoff=min(1.0,1/ratio)*.94
        FloatArray(48) { tap ->
            val x=tap-23-phase/1024.0
            val sinc=if(abs(x)<1e-12)cutoff else sin(PI*x*cutoff)/(PI*x)
            (sinc*(.5+.5*cos(PI*x/24))).toFloat()
        }.also { w -> val sum=w.sum();for(i in w.indices)w[i]/=sum }
    }
    private var window=FloatArray((ceil(4096*ratio).toInt()+64)*2)
    override fun read(frame:Long,count:Int,result:FloatArray) {
        result.fill(0f)
        val needed=minOf(count.toLong(),(frames-frame).coerceAtLeast(0)).toInt()
        if(needed==0)return
        val first=floor(frame*ratio).toLong()-23
        val end=floor((frame+needed-1)*ratio).toLong()+25
        val length=(end-first).toInt()
        if(window.size<length*2)window=FloatArray(length*2)
        // Stage a contiguous source window once. The FIR inner loop then uses
        // plain array offsets rather than two Long divisions per tap/sample.
        var copied=0
        while(copied<length) {
            val at=first+copied
            if(at<0) { window[copied*2]=0f;window[copied*2+1]=0f;copied++;continue }
            val start=at/4096*4096;val slot=((at/4096)%2).toInt()
            if(starts[slot]!=start) { source.read(start,4096,blocks[slot]);starts[slot]=start }
            val n=minOf(length-copied,(start+4096-at).toInt())
            blocks[slot].copyInto(window,copied*2,((at-start)*2).toInt(),((at-start+n)*2).toInt())
            copied+=n
        }
        repeat(needed) { i ->
            val position=(frame+i)*ratio;val base=floor(position).toLong()
            val w=weights[((position-base)*1024).toInt().coerceIn(0,1023)]
            val offset=((base-23-first)*2).toInt()
            var left=0f;var right=0f
            for(tap in w.indices) { val at=offset+tap*2;left+=window[at]*w[tap];right+=window[at+1]*w[tap] }
            result[i*2]=left;result[i*2+1]=right
        }
    }

    override fun close()=source.close()
}
