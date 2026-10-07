package com.atmytrack.app.audio

import android.content.Context
import com.atmytrack.app.data.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object NativePitch {
    init { System.loadLibrary("atmytrack_dsp") }
    external fun create(rate:Int,semitones:Int):Long
    external fun latency(handle:Long):Int
    external fun process(handle:Long,input:FloatArray,output:FloatArray,count:Int)
    external fun destroy(handle:Long)
}

class PitchRenderer(private val context:Context) {
    suspend fun render(p:Project,semitones:Int,ids:List<String>,progress:(String)->Unit):List<Stem> {
        require(semitones in -12..12)
        return p.stems.map { s ->
            if(s.id !in ids || semitones==0) s.copy(pitchFile="",pitchApplied=0)
            else {
                val key="${s.id}-${s.fingerprint}-$semitones"
                val relative="pitch/${p.id}/$key.pcm"; val target=File(context.filesDir,relative)
                if(!target.exists() || target.length()!=s.frames*8) {
                    target.parentFile!!.mkdirs()
                    check(target.parentFile!!.usableSpace > s.frames*8 + 32L*1024*1024) { "Espaço insuficiente para preparar ${s.name}." }
                    val part=File(target.path+".part")
                    val dsp=NativePitch.create(p.sampleRate,semitones)
                    try {
                        val latency=NativePitch.latency(dsp)
                        val input=FloatArray(8192); val output=FloatArray(8192)
                        val bytes=ByteBuffer.allocate(8192*4).order(ByteOrder.LITTLE_ENDIAN)
                        Readers.open(context,s,p.sampleRate,4096,original=true).use { reader -> part.outputStream().buffered(1024*1024).use { stream ->
                            var pos=0L
                            while(pos<s.frames+latency) {
                                coroutineContext.ensureActive()
                                val count=minOf(4096L,s.frames+latency-pos).toInt()
                                reader.read(pos,count,input)
                                NativePitch.process(dsp,input,output,count)
                                val skip=(latency-pos).coerceIn(0,count.toLong()).toInt()
                                bytes.clear()
                                for(i in skip until count) { bytes.putFloat(output[i*2]); bytes.putFloat(output[i*2+1]) }
                                stream.write(bytes.array(),0,bytes.position())
                                pos+=count
                                if(pos%65536L<count)progress("Alterando tom: ${s.name} • ${(pos*100/(s.frames+latency)).coerceAtMost(100)}%")
                            }
                        } }
                        check(part.length()==s.frames*8) { "Duração do pitch inválida." }
                        check(part.renameTo(target)) { "Não foi possível salvar o pitch." }
                    } finally { NativePitch.destroy(dsp); part.delete() }
                }
                s.copy(pitchFile=relative,pitchApplied=semitones)
            }
        }
    }
}
