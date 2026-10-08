package com.atmytrack.app.audio

import com.atmytrack.app.data.*
import kotlin.math.*

/** Peak bins accumulated while copying/decoding, with partial PCM frames retained. */
class WaveformAccumulator(private val frames:Long) {
    val peaks=FloatArray(512)
    private val carry=ByteArray(8)
    private var carried=0
    private var rawFrame=0L
    fun add(samples:FloatArray,count:Int,start:Long) {
        repeat(count) { i -> put(start+i,max(abs(samples[i*2]),abs(samples[i*2+1]))) }
    }
    private fun put(frame:Long,value:Float) {
        if(frames>0 && frame<frames && value.isFinite()) { val bin=(frame*512/frames).toInt().coerceIn(0,511);peaks[bin]=max(peaks[bin],value.coerceIn(0f,1f)) }
    }
    fun addRaw(bytes:ByteArray,count:Int,offset:Long,format:PcmFormat) {
        var pos=(format.offset-offset).coerceIn(0,count.toLong()).toInt()
        val end=minOf(count.toLong(),format.offset+frames*format.frameBytes-offset).coerceAtLeast(0).toInt()
        if(pos>=end)return
        fun sample(data:ByteArray,p:Int):Float {
            fun u(i:Int)=data[i].toInt() and 255
            fun int32()=u(p) or (u(p+1) shl 8) or (u(p+2) shl 16) or (data[p+3].toInt() shl 24)
            return when(format.encoding) {
                PcmEncoding.U8 -> (u(p)-128)/128f
                PcmEncoding.S16 -> ((u(p) or (data[p+1].toInt() shl 8)))/32768f
                PcmEncoding.S24 -> ((u(p) or (u(p+1) shl 8) or (data[p+2].toInt() shl 16)))/8388608f
                PcmEncoding.S32 -> int32()/2147483648f
                PcmEncoding.FLOAT32 -> Float.fromBits(int32())
            }
        }
        fun consume(data:ByteArray,p:Int) { var value=abs(sample(data,p));if(format.channels==2)value=max(value,abs(sample(data,p+format.frameBytes/2)));put(rawFrame++,value) }
        if(carried>0) { val n=minOf(format.frameBytes-carried,end-pos);bytes.copyInto(carry,carried,pos,pos+n);carried+=n;pos+=n;if(carried==format.frameBytes){consume(carry,0);carried=0} }
        while(pos+format.frameBytes<=end){consume(bytes,pos);pos+=format.frameBytes}
        if(pos<end){carried=end-pos;bytes.copyInto(carry,0,pos,end)}
    }
}
