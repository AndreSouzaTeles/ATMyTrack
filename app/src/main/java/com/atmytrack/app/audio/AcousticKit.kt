package com.atmytrack.app.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bundled CC0 instrument recordings, decoded once off the playback thread. */
class AcousticKit private constructor(val samples:Map<String,List<FloatArray>>) {
    companion object {
        fun load(open:(String)->InputStream):AcousticKit {
            val rows=open("drumkit/bank.tsv").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
            val samples=linkedMapOf<String,MutableList<FloatArray>>()
            for(row in rows) {
                val (role,file)=row.split('\t')
                val bytes=open("drumkit/$file").use { it.readBytes() }
                val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                require(String(bytes,0,4,Charsets.US_ASCII)=="RIFF" && String(bytes,8,4,Charsets.US_ASCII)=="WAVE")
                require(b.getShort(20).toInt()==1 && b.getShort(22).toInt()==2 && b.getInt(24)==48000 && b.getShort(34).toInt()==16)
                require(String(bytes,36,4,Charsets.US_ASCII)=="data" && b.getInt(40)==bytes.size-44)
                b.position(44);val pcm=FloatArray((bytes.size-44)/2) { b.short/32768f }
                require(pcm.isNotEmpty());samples.getOrPut(role){mutableListOf()}.add(pcm)
            }
            require(listOf("kick","snare","ghost","hat","openhat","stick","conga","shaker","tamb","tom","crash","ride").all { samples[it]?.size==2 })
            return AcousticKit(samples)
        }
    }
}
