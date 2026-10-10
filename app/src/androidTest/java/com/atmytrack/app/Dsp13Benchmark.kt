package com.atmytrack.app
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class Dsp13Benchmark {
 @Test fun compareSerialParallelAndRetainedCache():Unit=runBlocking {
    val c=InstrumentationRegistry.getInstrumentation().targetContext
    val wav=ImportBenchmark().wav(10,2,24)
    val p=AudioImporter(c).import(List(4){Uri.fromFile(wav)},"Benchmark DSP") {}.copy(speed=80,semitones=2)
    val ids=p.stems.map { it.id }
    var start=SystemClock.elapsedRealtime()
    val baseline=LegacyPitchRenderer(c).render(p.copy(id=p.id+"-baseline"),2,ids) {}
    val old=SystemClock.elapsedRealtime()-start
    start=SystemClock.elapsedRealtime();val first=PitchRenderer(c).render(p,2,ids) {};val fresh=SystemClock.elapsedRealtime()-start
    val a=FloatArray(8192);val b=FloatArray(8192)
    Readers.open(c,baseline[0],48000,4096).use { it.read(48000,4096,a) };Readers.open(c,first[0],48000,4096).use { it.read(48000,4096,b) }
    for(i in a.indices)assertEquals(a[i],b[i],.00001f)
    val q=p.copy(stems=first);PitchRenderer(c).prune(listOf(q),p.id)
    val other=PitchRenderer(c).render(p.copy(speed=120),2,ids) {};PitchRenderer(c).prune(listOf(p.copy(speed=120,stems=other)),p.id)
    start=SystemClock.elapsedRealtime();val cached=PitchRenderer(c).render(p,2,ids) {};val reuse=SystemClock.elapsedRealtime()-start
    assertEquals(first,cached)
    android.util.Log.i("DSP13","4 tracks 10s stereo PCM24 48kHz pitch+2 speed80 bytesEach=${wav.length()} serialMs=$old parallelMs=$fresh cachedMs=$reuse")
 }
}
