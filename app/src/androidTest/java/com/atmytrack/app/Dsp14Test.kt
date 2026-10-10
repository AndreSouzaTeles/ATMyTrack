package com.atmytrack.app
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class Dsp14Test {
 @Test fun boundedParallelRenderAndWarmCache():Unit=runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val imported=AudioImporter(c).import(listOf(Uri.fromFile(ImportBenchmark().wav(20,2,24))),"DSP 14") {}
  val timings=mutableListOf<Long>()
  for(slots in listOf(2,4)) {
   val stems=List(8){imported.stems.first().copy(id=UUID.randomUUID().toString())}
   val p=imported.copy(id=UUID.randomUUID().toString(),stems=stems,speed=80,speedTracks=stems.map{it.id})
   val start=SystemClock.elapsedRealtime();val rendered=PitchRenderer(c,slots).render(p,2,stems.map{it.id}) {};val elapsed=SystemClock.elapsedRealtime()-start;timings.add(elapsed)
   assertEquals(8,rendered.size);assertTrue(rendered.all{it.dspSpeed==80&&it.pitchApplied==2})
   val warm=SystemClock.elapsedRealtime();PitchRenderer(c,slots).render(p,2,stems.map{it.id}) {};val cached=SystemClock.elapsedRealtime()-warm
   android.util.Log.i("DSP14","8 independent stems same synthetic stereo PCM24 20s 48kHz pitch+2 speed80: slots=$slots cold=${elapsed}ms warm=${cached}ms cores=${Runtime.getRuntime().availableProcessors()}")
   assertTrue(cached<elapsed)
   java.io.File(c.filesDir,"pitch/${p.id}").deleteRecursively()
  }
  android.util.Log.i("DSP14","Parallel cold ratio=${timings[0].toDouble()/timings[1]}")
 }
}
