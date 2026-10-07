package com.atmytrack.app.audio

import android.content.Context
import android.media.*
import android.net.Uri
import com.atmytrack.app.data.*
import java.io.File
import java.nio.ByteOrder
import kotlin.math.*

/** One decoder feeds bounded blocks indexed by presentation timestamp, never an independent player. */
class StreamingReader(private val context: Context, private val stem: Stem, private val rate: Int) : FrameReader {
    private var extractor=MediaExtractor()
    private lateinit var codec: MediaCodec
    private val info=MediaCodec.BufferInfo()
    private var channels=stem.format.channels
    private var encoding=AudioFormat.ENCODING_PCM_16BIT
    private var inputDone=false
    private var done=false
    private var samples=FloatArray(0)
    private var start=0L
    private var count=0
    private var nextRead=0L
    private var continuousEnd: Long?=null
    private var adts:AdtsTiming?=null
    private var exactPacketScan=false
    init { open() }
    private fun open() {
        try {
            if(stem.external) extractor.setDataSource(context,Uri.parse(stem.source),null)
            else extractor.setDataSource(File(context.filesDir,stem.pcm).path)
            val index=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
            extractor.selectTrack(index)
            val f=extractor.getTrackFormat(index)
            exactPacketScan=f.getString(MediaFormat.KEY_MIME) in listOf("audio/mpeg","audio/vorbis")
            if(f.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm") {
                val input=if(stem.external)SeekInput.uri(context,Uri.parse(stem.source)) else SeekInput.file(File(context.filesDir,stem.pcm))
                adts=input.use { AdtsTiming.read(it) }
            }
            codec=MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            try { codec.configure(f,null,null,0); codec.start() } catch(e:Exception) { codec.release(); throw e }
        } catch(e:Exception) { extractor.release(); throw e }
    }
    private fun seek(frame: Long) {
        if(frame<=rate || exactPacketScan) {
            runCatching { codec.stop() };codec.release();extractor.release();extractor=MediaExtractor();open()
            if(exactPacketScan && frame>rate) {
                // MP3 VBR seek tables and Vorbis page seeks are approximate. Advancing
                // encoded packets preserves the original timestamps without decoding
                // or copying the preceding audio, and uses constant memory.
                val target=(frame-rate)*1_000_000L/rate
                while(extractor.sampleTime in 0 until target && extractor.advance()) { }
            }
        } else {
            extractor.seekTo(((frame-rate).coerceAtLeast(0)*1_000_000L/rate),MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
        }
        inputDone=false; done=false; count=0; continuousEnd=null
    }
    override fun read(frame: Long, count: Int, result: FloatArray) {
        result.fill(0f)
        if(frame>=stem.frames) return
        if(frame < nextRead || frame-nextRead > rate*2L) seek(frame)
        var pos=frame; var offset=0
        val needed=minOf(count.toLong(),stem.frames-frame).toInt()
        while(offset<needed) {
            if(this.count==0 || pos>=start+this.count) { if(done)break; pump(); continue }
            if(pos<start) { val silence=minOf((start-pos), (needed-offset).toLong()).toInt(); pos+=silence; offset+=silence; continue }
            val n=minOf(needed-offset,(start+this.count-pos).toInt())
            samples.copyInto(result,offset*2,(pos-start).toInt()*2,((pos-start).toInt()+n)*2)
            offset+=n; pos+=n
        }
        nextRead=frame+count
    }
    private fun pump() {
        val deadline=System.nanoTime()+15_000_000_000L
        while(true) {
            var fed=0
            while(!inputDone && fed<4) {
                val index=codec.dequeueInputBuffer(0); if(index<0)break
                val size=extractor.readSampleData(codec.getInputBuffer(index)!!,0)
                if(size<0) { codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                else { codec.queueInputBuffer(index,0,size,adts?.timestamp(extractor.sampleTime) ?: extractor.sampleTime,0); extractor.advance() }
                fed++
            }
            val index=codec.dequeueOutputBuffer(info,1000)
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f=codec.outputFormat
                require(f.getInteger(MediaFormat.KEY_SAMPLE_RATE)==rate) { "Decoder alterou sample rate." }
                channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); require(channels in 1..2)
                encoding=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            } else if(index>=0) {
                try {
                    done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                    if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                        val b=codec.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        b.position(info.offset); b.limit(info.offset+info.size)
                        val bytes=when(encoding) { AudioFormat.ENCODING_PCM_FLOAT,AudioFormat.ENCODING_PCM_32BIT->4; AudioFormat.ENCODING_PCM_24BIT_PACKED->3; AudioFormat.ENCODING_PCM_8BIT->1; else->2 }
                        count=info.size/(channels*bytes)
                        if(samples.size<count*2)samples=FloatArray(count*2)
                        fun sample():Float=when(encoding) {
                            AudioFormat.ENCODING_PCM_FLOAT->b.float.let { if(it.isFinite())it else 0f }
                            AudioFormat.ENCODING_PCM_32BIT->b.int/2147483648f
                            AudioFormat.ENCODING_PCM_24BIT_PACKED->((b.get().toInt() and 255) or ((b.get().toInt() and 255) shl 8) or (b.get().toInt() shl 16))/8388608f
                            AudioFormat.ENCODING_PCM_8BIT->((b.get().toInt() and 255)-128)/128f
                            else->b.short/32768f
                        }
                        repeat(count) { val a=sample(); samples[it*2]=a; samples[it*2+1]=if(channels==1)a else sample() }
                        val pts=(info.presentationTimeUs*rate/1_000_000.0).roundToLong()
                        start=continuousEnd ?: pts
                        continuousEnd=start+count
                        return
                    }
                    if(done) { count=0; return }
                } finally { codec.releaseOutputBuffer(index,false) }
            }
            check(System.nanoTime()<deadline) { "Decoder sem resposta: ${stem.name}." }
        }
    }
    override fun close() { runCatching { codec.stop() }; codec.release(); extractor.release() }
}

object Readers {
    fun open(context:Context,s:Stem,rate:Int,block:Int=512,original:Boolean=false):FrameReader {
        if(!original && s.pitchFile.isNotEmpty()) return PcmReader(File(context.filesDir,s.pitchFile),kotlin.math.round(s.frames/(s.dspSpeed/100.0)).toLong(),block,PcmFormat())
        if(s.sourceRate!=0 && s.sourceRate!=rate) {
            val native=s.copy(frames=if(s.sourceFrames>0)s.sourceFrames else (s.frames.toDouble()*s.sourceRate/rate).roundToLong())
            return ResampledReader(open(context,native,s.sourceRate,4096,original),s.sourceRate,rate,s.frames)
        }
        if(s.compressed)return StreamingReader(context,s,rate)
        val input=if(s.external)SeekInput.uri(context,Uri.parse(s.source)) else SeekInput.file(File(context.filesDir,s.pcm))
        try {
            check(input.length()>=s.format.offset+s.frames*s.format.frameBytes) { "Track ausente/incompleta: ${s.name}." }
            return PcmReader(input,s.frames,block,s.format)
        } catch(e:Exception) { input.close(); throw e }
    }
}
