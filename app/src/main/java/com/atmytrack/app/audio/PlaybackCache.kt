package com.atmytrack.app.audio

import android.content.Context
import android.net.Uri
import com.atmytrack.app.data.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Decode compressed stems once, outside playback. Native PCM is not transcoded.
 * Content-provider PCM is copied byte-for-byte to keep SAF off the playback path.
 * Atomic publication and a versioned fingerprint prevent reuse of partial caches. */
class PlaybackCache(private val context:Context) {
    private fun key(s:Stem,rate:Int)=MessageDigest.getInstance("SHA-256").digest("v1:${s.source}:${s.pcm}:${s.fingerprint}:${s.frames}:${s.format}:$rate:${s.sourceRate}".toByteArray()).joinToString(""){"%02x".format(it)}
    /** Called only at startup while the engine is idle. */
    fun prune(projects:List<Project>) {
        val retained=projects.flatMap { p -> p.stems.map { key(it,p.sampleRate) } }.toSet()
        val root=File(context.filesDir,"playback").canonicalFile
        root.listFiles()?.filter { it.isFile && it.name.substringBefore('.') !in retained }?.forEach {
            if(it.canonicalFile.parentFile==root)it.delete()
        }
    }
    fun envelope(s:Stem,rate:Int):List<Float>? = runCatching {
        val array=org.json.JSONArray(File(context.filesDir,"playback/${key(s,rate)}.wave.json").readText())
        require(array.length()==512)
        (0 until 512).map { array.getDouble(it).toFloat() }
    }.getOrNull()
    fun saveEnvelope(s:Stem,rate:Int,peaks:FloatArray) {
        val target=File(context.filesDir,"playback/${key(s,rate)}.wave.json");target.parentFile!!.mkdirs()
        val part=File(target.path+".${java.util.UUID.randomUUID()}.part")
        try { part.writeText(org.json.JSONArray(peaks.toList()).toString());check(part.renameTo(target)) }
        catch(e:Exception) { android.util.Log.w("ATMyTrack","Não foi possível guardar a waveform; o áudio continua disponível",e) }
        finally { part.delete() }
    }
    fun open(s:Stem,rate:Int,checkCancelled:()->Unit={},progress:(Int)->Unit={}):FrameReader {
        checkCancelled()
        if(s.pitchFile.isNotEmpty())return Readers.open(context,s,rate,4096)
        val needsDecode=s.compressed || (s.sourceRate!=0 && s.sourceRate!=rate)
        val remote=s.external && Uri.parse(s.source).scheme!="file"
        if(!needsDecode && !remote)return Readers.open(context,s,rate,4096)
        val key=key(s,rate)
        val dir=File(context.filesDir,"playback").apply { mkdirs() }
        val file=File(dir,"$key.${if(needsDecode)"pcm" else "wav"}")
        val expected=if(needsDecode)s.frames*8 else s.format.offset+s.frames*s.format.frameBytes
        if(!file.exists() || (if(needsDecode)file.length()!=expected else file.length()<expected)) {
            check(dir.usableSpace>expected+32L*1024*1024) { "Espaço insuficiente para preparar ${s.name}: ${expected/1048576} MB necessários." }
            val part=File(dir,"$key-${java.util.UUID.randomUUID()}.part")
            val envelope=WaveformAccumulator(s.frames)
            try {
                if(needsDecode) {
                    Readers.open(context,s,rate,4096).use { reader ->
                        part.outputStream().buffered(256*1024).use { out ->
                            val samples=FloatArray(8192)
                            val bytes=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN)
                            val floats=bytes.asFloatBuffer()
                            var frame=0L;var percent=-1
                            while(frame<s.frames) {
                                checkCancelled()
                                val count=minOf(4096L,s.frames-frame).toInt()
                                reader.read(frame,count,samples)
                                envelope.add(samples,count,frame)
                                floats.position(0);floats.put(samples,0,count*2)
                                out.write(bytes.array(),0,count*8)
                                frame+=count
                                val next=(frame*100/s.frames).toInt()
                                if(next!=percent) { percent=next;progress(percent) }
                            }
                        }
                    }
                } else {
                    val input=context.contentResolver.openInputStream(Uri.parse(s.source)) ?: error("Origem inacessível: ${s.name}")
                    input.use { source -> part.outputStream().buffered(256*1024).use { out ->
                        val bytes=ByteArray(256*1024);var copied=0L
                        while(true) { checkCancelled();val n=source.read(bytes);if(n<0)break;out.write(bytes,0,n);envelope.addRaw(bytes,n,copied,s.format);copied+=n;progress((copied*100/expected).coerceAtMost(100).toInt()) }
                    } }
                }
                check(part.length()>=expected) { "Cache incompleto: ${s.name}" }
                if(!part.renameTo(file))check(file.length()>=expected) { "Não foi possível salvar cache: ${s.name}" }
                saveEnvelope(s,rate,envelope.peaks)
            } finally { part.delete() }
        }
        checkCancelled()
        return PcmReader(file,s.frames,4096,if(needsDecode)PcmFormat() else s.format)
    }
}
