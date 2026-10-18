package com.atmytrack.app.separation

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** IEEE float WAV retains separation headroom; no independent limiting or time shifts. */
class FloatWav(file:File,private val frames:Long):AutoCloseable {
    private val out=RandomAccessFile(file,"rw")
    private var written=0L
    init {
        require(frames in 1..((0xffffffffL-36)/8)) { "Áudio excede o limite WAV de 4 GB por faixa." }
        val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36+frames*8).toInt()).put("WAVEfmt ".toByteArray()).putInt(16)
        h.putShort(3).putShort(2).putInt(44100).putInt(44100*8).putShort(8).putShort(32)
        h.put("data".toByteArray()).putInt((frames*8).toInt());out.setLength(0);out.write(h.array())
    }
    fun write(samples:FloatArray,offset:Int,count:Int) {
        require(written+count<=frames)
        val b=ByteBuffer.allocate(count*8).order(ByteOrder.LITTLE_ENDIAN)
        for(i in offset*2 until (offset+count)*2) { require(samples[i].isFinite());b.putFloat(samples[i]) }
        out.write(b.array());written+=count
    }
    fun finish() { check(written==frames) { "Stem incompleta: $written/$frames frames." };out.fd.sync() }
    override fun close()=out.close()
}
