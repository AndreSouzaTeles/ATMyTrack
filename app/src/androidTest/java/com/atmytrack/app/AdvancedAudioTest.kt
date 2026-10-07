package com.atmytrack.app

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
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class AdvancedAudioTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun pitchChangesFrequencyPreservesFramesAndOnlySelectedTracks()=runBlocking {
        val source=ImportBenchmark().wav(4,1,16)
        val p=AudioImporter(context).import(listOf(Uri.fromFile(source),Uri.fromFile(source)),"Pitch") {}
        val original=p.stems[0]; val untouched=p.stems[1]
        val shifted=PitchRenderer(context).render(p,3,listOf(original.id)) {}
        assertEquals(untouched,shifted[1]); assertEquals(3,shifted[0].pitchApplied)
        assertEquals(original.frames*8,File(context.filesDir,shifted[0].pitchFile).length())
        val block=FloatArray(96000)
        Readers.open(context,shifted[0],48000,48000).use { it.read(48000,48000,block) }
        var crossings=0
        for(i in 1 until 48000)if(block[(i-1)*2]<=0 && block[i*2]>0)crossings++
        val expected=220*2.0.pow(3.0/12)
        assertEquals(expected,crossings.toDouble(),2.0)
        assertTrue(block.all { it.isFinite() }); assertTrue(block.maxOrNull()!!>.1)
        for(semitones in listOf(-12,12)) {
            val version=PitchRenderer(context).render(p,semitones,listOf(original.id)) {}.first()
            assertEquals(original.frames*8,File(context.filesDir,version.pitchFile).length())
            Readers.open(context,version,48000,48000).use { it.read(48000,48000,block) }
            var count=0
            for(i in 1 until 48000)if(block[(i-1)*2]<=0 && block[i*2]>0)count++
            assertEquals(220*2.0.pow(semitones/12.0),count.toDouble(),2.0)
        }
        val reset=PitchRenderer(context).render(p.copy(stems=shifted),0,emptyList()) {}
        assertTrue(reset.all { it.pitchFile.isEmpty() && it.pitchApplied==0 })
        File(context.getExternalFilesDir(null),"pitch-test.txt").writeText("+3 semitones: $crossings Hz, expected $expected Hz; ${original.frames} input frames; ${File(context.filesDir,shifted[0].pitchFile).length()/8} output frames; unselected track unchanged.")
    }
    @Test fun compressedStreamingSeeksMatchSequentialTimeline()=runBlocking {
        val source=ImportBenchmark().aac(8)
        val p=AudioImporter(context).import(listOf(Uri.fromFile(source)),"AAC") {}
        val s=p.stems.single(); assertTrue(s.compressed && s.external); assertEquals("",s.pcm)
        val expected=FloatArray(s.frames.toInt()*2); val block=FloatArray(1024)
        Readers.open(context,s,p.sampleRate).use { r ->
            var frame=0L
            while(frame<s.frames) { val n=minOf(512L,s.frames-frame).toInt(); r.read(frame,n,block); block.copyInto(expected,(frame*2).toInt(),0,n*2);frame+=n }
        }
        Readers.open(context,s,p.sampleRate).use { r ->
            for(frame in listOf(48000L,250000L,90000L,0L,s.frames-250)) {
                r.read(frame,512,block)
                val n=minOf(512L,s.frames-frame).toInt()
                var error=0f
                repeat(n*2) { error=max(error,abs(block[it]-expected[(frame*2).toInt()+it])) }
                assertTrue("AAC seek error at $frame: $error",error<.002f)
            }
        }
    }
    @Test fun twelveCompressedStemsImportWithoutAudioCopiesAndPlayTogether()=runBlocking {
        val source=ImportBenchmark().aac(8)
        val start=SystemClock.elapsedRealtime()
        val p=AudioImporter(context).import(List(12){Uri.fromFile(source)},"12 AAC") {}
        val elapsed=SystemClock.elapsedRealtime()-start
        assertTrue(p.stems.all { it.compressed && it.external && it.pcm.isEmpty() })
        val engine=(context.applicationContext as TrackApplication).engine
        engine.load(p.copy(master=.05f)); await { engine.state.value.projectId==p.id }
        engine.play(); await { engine.state.value.playing && engine.state.value.frame>48000 }
        assertEquals(12,engine.state.value.peaks.size)
        assertTrue(engine.state.value.peaks.all { abs(it-engine.state.value.peaks[0])<.0001f })
        val before=engine.state.value.frame
        engine.update(p.copy(stems=p.stems.reversed(),master=.05f)); await { engine.state.value.frame>before+10000 }
        engine.pause(); await { !engine.state.value.playing }
        engine.seek(144000); await { engine.state.value.frame==144000L }
        engine.play(); await { engine.state.value.playing && engine.state.value.frame>160000 }
        File(context.getExternalFilesDir(null),"twelve-stems.txt").writeText("12 AAC 8s/48k/stereo metadata import: ${elapsed}ms; 0 copied audio bytes; ${engine.state.value.underruns} underruns; identical stem meters; seek, pause and reorder passed.")
        engine.stop(); await { !engine.state.value.playing }; engine.unload()
    }
    @Test fun fingerprintInvalidatesReplacedSourceAndWaveformCache()=runBlocking {
        val source=ImportBenchmark().wav(2,1,16)
        val uri=Uri.fromFile(source); val first=SourceFingerprint.get(context,uri)
        val p=AudioImporter(context).import(listOf(uri),"Wave") {}
        val analysis=Analysis(context); var count=0
        val wave=analysis.waveform(p,{true}) { count++ }
        assertEquals(512,wave.size); assertTrue(wave.any { it>.3 })
        analysis.waveform(p,{error("Cached waveform must not scan")}) {}
        java.io.RandomAccessFile(source,"rw").use { it.seek(44);it.writeInt(1234567) }
        val second=SourceFingerprint.get(context,uri); assertNotEquals(first,second)
        assertNotEquals(analysis.key(p),analysis.key(p.copy(stems=p.stems.map { it.copy(fingerprint=second) })))
    }
    private fun await(predicate:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+20000
        while(!predicate()) { assertTrue("Timed out: ${(context.applicationContext as TrackApplication).engine.state.value}",SystemClock.elapsedRealtime()<deadline);Thread.sleep(25) }
    }
}
