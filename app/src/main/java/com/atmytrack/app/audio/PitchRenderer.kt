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
    external fun stretchLatency(handle:Long,speed:Double):Int
    external fun stretch(handle:Long,input:FloatArray,output:FloatArray,inputCount:Int,outputCount:Int)
    external fun destroy(handle:Long)
}

/** One offline pass combines independent pitch and speed selections. Playback remains PCM only. */
class PitchRenderer(private val context:Context) {
    suspend fun render(p:Project,semitones:Int,ids:List<String>,progress:(String)->Unit):List<Stem> {
        require(semitones in -12..12 && p.speed in 50..200)
        return p.stems.mapIndexed { index,s ->
            val pitch=if(s.id in ids)semitones else 0
            val speed=if(s.id in p.selectedSpeedTracks)p.speed else 100
            if(pitch==0 && speed==100) s.copy(pitchFile="",pitchApplied=0,dspSpeed=100)
            else {
                val ratio=speed/100.0
                val frames=kotlin.math.round(s.frames/ratio).toLong()
                val key="v3-${s.id}-${s.fingerprint}-${p.sampleRate}-$pitch-$speed"
                val relative="pitch/${p.id}/$key.pcm"; val target=File(context.filesDir,relative)
                if(!target.exists() || target.length()!=frames*8) {
                    target.parentFile!!.mkdirs()
                    check(target.parentFile!!.usableSpace > frames*8 + 32L*1024*1024) { "Espaço insuficiente para preparar ${s.name}." }
                    val part=File(target.path+".part")
                    val dsp=NativePitch.create(p.sampleRate,pitch)
                    try {
                        val latency=NativePitch.stretchLatency(dsp,ratio)
                        val input=FloatArray(8192); val output=FloatArray(8192)
                        val bytes=ByteBuffer.allocate(8192*4).order(ByteOrder.LITTLE_ENDIAN)
                        PlaybackCache(context).open(s.copy(pitchFile="",pitchApplied=0,dspSpeed=100),p.sampleRate).use { reader -> part.outputStream().buffered(256*1024).use { stream ->
                            var pos=0L; var produced=0L; var written=0L
                            while(written<frames) {
                                coroutineContext.ensureActive()
                                val count=2048
                                reader.read(pos,count,input)
                                val next=kotlin.math.round((pos+count)/ratio).toLong()
                                val outCount=(next-produced).toInt()
                                NativePitch.stretch(dsp,input,output,count,outCount)
                                val skip=(latency-produced).coerceIn(0,outCount.toLong()).toInt()
                                val take=minOf(outCount-skip,(frames-written).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                bytes.clear()
                                for(i in skip until skip+take) { bytes.putFloat(output[i*2]); bytes.putFloat(output[i*2+1]) }
                                stream.write(bytes.array(),0,bytes.position())
                                written+=take;pos+=count;produced=next
                                if(pos%65536L<count)progress("Preparando $speed% • Track ${index+1}/${p.stems.size} • ${s.name} • ${written*100/frames}%")
                            }
                        } }
                        check(part.length()==frames*8) { "Preparação incompleta: ${s.name}." }
                        check(part.renameTo(target)) { "Não foi possível salvar ${s.name}." }
                    } catch(e:Exception) {
                        if(e is kotlinx.coroutines.CancellationException)throw e
                        android.util.Log.e("ATMyTrack-DSP","Track ${s.name}",e)
                        throw IllegalStateException("Não foi possível preparar a track ${s.name}. Verifique espaço e acesso ao arquivo e tente novamente.",e)
                    } finally { NativePitch.destroy(dsp); part.delete() }
                }
                target.setLastModified(System.currentTimeMillis())
                s.copy(pitchFile=relative,pitchApplied=pitch,dspSpeed=speed)
            }
        }
    }
    /** Remove obsolete transforms, then evict least-recent inactive caches. Never touch original audio. */
    fun prune(projects:List<Project>,protectedId:String="") {
        val keep=projects.flatMap { it.stems }.map { it.pitchFile }.toSet()
        val root=File(context.filesDir,"pitch").canonicalFile
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            if(file.canonicalPath.startsWith(root.path+File.separator) && file.relativeTo(context.filesDir).invariantSeparatorsPath !in keep)file.delete()
        }
        val files=root.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var bytes=files.sumOf { it.length() }
        for(file in files) {
            if(bytes<=2L*1024*1024*1024)break
            if(file.parentFile.name!=protectedId) { val length=file.length();if(file.delete())bytes-=length }
        }
    }
}
