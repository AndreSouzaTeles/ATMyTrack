package com.atmytrack.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.*
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Regression benchmark: the legacy implementation exists only in the test APK. */
@RunWith(AndroidJUnit4::class)
class ImportBenchmark {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    internal fun wav(seconds: Int, channels: Int, bits: Int): File {
        val rate = 48000; val frames = seconds * rate; val frameBytes = channels * bits / 8
        val file = File(context.cacheDir, "benchmark-$channels-$bits.wav")
        file.outputStream().buffered(1024 * 1024).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()); header.putInt(36 + frames * frameBytes); header.put("WAVEfmt ".toByteArray()); header.putInt(16)
            header.putShort(1); header.putShort(channels.toShort()); header.putInt(rate); header.putInt(rate * frameBytes); header.putShort(frameBytes.toShort()); header.putShort(bits.toShort())
            header.put("data".toByteArray()); header.putInt(frames * frameBytes); out.write(header.array())
            val second = ByteBuffer.allocate(rate * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
            repeat(rate) { i -> repeat(channels) { c ->
                val value = sin(2 * PI * (220 + 110 * c) * i / rate) * .4
                if (bits == 16) second.putShort((value * 32767).toInt().toShort())
                else { val sample = (value * 8388607).toInt(); second.put(sample.toByte()); second.put((sample shr 8).toByte()); second.put((sample shr 16).toByte()) }
            } }
            repeat(seconds) { out.write(second.array()) }
        }
        return file
    }
    internal fun aac(seconds: Int): File {
        val file = File(context.cacheDir, "benchmark.m4a")
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            var sent = 0; var inputDone = false; var done = false; var track = -1
            val info = MediaCodec.BufferInfo(); val deadline = SystemClock.elapsedRealtime() + 90000
            while (!done) {
                check(SystemClock.elapsedRealtime() < deadline) { "Fixture AAC encoder timed out" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(0)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val count = minOf(buffer.capacity() / 4, seconds * 48000 - sent)
                        buffer.clear()
                        repeat(count) { i -> val sample = (sin(2 * PI * 330 * (sent + i) / 48000) * 10000).toInt().toShort(); buffer.putShort(sample); buffer.putShort(sample) }
                        codec.queueInputBuffer(index, 0, count * 4, sent * 1_000_000L / 48000, if (count == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        sent += count; inputDone = count == 0
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 1000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                else if (index >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
        } finally { runCatching { codec.stop() }; codec.release(); if (started) muxer.stop(); muxer.release() }
        return file
    }
    @Test fun compareSameFilesAgainstVersion010() = runBlocking {
        val rows = mutableListOf("Android ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}; API ${android.os.Build.VERSION.SDK_INT}",
            "Files generated locally; generation excluded. Two passes with reversed order. New importer 0.4.0 reads persistent local metadata without copying. Baseline is the retained 0.1.0 full decoder.",
            "format,pass,input_bytes,legacy_ms,new_ms,legacy_cache_bytes,new_cache_bytes,max_sample_error,legacy_frames,new_frames,errors_start_mid_tail")
        val fixtures = listOf("WAV mono16 300s" to wav(300,1,16), "WAV stereo24 180s" to wav(180,2,24), "AAC stereo 60s" to aac(60))
        try {
            for ((label, source) in fixtures) repeat(2) { pass ->
                suspend fun measured(legacy: Boolean): Pair<Project, Long> {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val project = if (legacy) LegacyAudioImporter(context).import(listOf(Uri.fromFile(source)), "Benchmark") {}
                        else AudioImporter(context).import(listOf(Uri.fromFile(source)), "Benchmark") {}
                    return project to ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000)
                }
                val first = measured(pass == 0); val second = measured(pass != 0)
                val old = if (pass == 0) first else second; val new = if (pass == 0) second else first
                try {
                    val a = old.first.stems.single(); val b = new.first.stems.single()
                    assertEquals(old.first.sampleRate, new.first.sampleRate); assertEquals(a.frames, b.frames)
                    assertTrue(b.external); assertEquals("",b.pcm)
                    val af = File(context.filesDir, a.pcm); val bf = File(context.filesDir, b.pcm)
                    var error = 0f
                    val errors=mutableListOf<Float>()
                    PcmReader(af,a.frames,format=a.format).use { ar -> Readers.open(context,b,new.first.sampleRate).use { br ->
                        val x = FloatArray(1024); val y = FloatArray(1024)
                        for (pos in listOf(0L, a.frames/2, a.frames-300)) {
                            ar.read(pos,512,x); br.read(pos,512,y)
                            var atPosition=0f
                            repeat(1024) { atPosition = max(atPosition, abs(x[it]-y[it])) }
                            error=max(error,atPosition); errors+=atPosition
                        }
                    } }
                    assertTrue("Decoded samples differ: $error", error <= 2f/32768)
                    rows += "$label,${pass+1},${source.length()},${old.second},${new.second},${af.length()},0,$error,${a.frames},${b.frames},${errors.joinToString("/")}"
                    File(context.getExternalFilesDir(null), "import-benchmark.txt").writeText(rows.joinToString("\n"))
                } finally {
                    File(context.filesDir,"audio/${old.first.id}").deleteRecursively()
                    File(context.filesDir,"audio/${new.first.id}").deleteRecursively()
                }
            }
        } finally { fixtures.forEach { it.second.delete() } }
    }
    @Test fun renderActualIconAsset() {
        val bitmap = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f,0f,512f,512f,112f,112f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16,18,20) })
        context.getDrawable(R.drawable.logo)!!.apply { setBounds(0,0,512,512); draw(canvas) }
        File(context.getExternalFilesDir(null),"icon-0.2.0.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
