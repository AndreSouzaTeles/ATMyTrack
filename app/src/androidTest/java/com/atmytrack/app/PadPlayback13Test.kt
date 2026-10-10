package com.atmytrack.app
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
class PadPlayback13Test {
 @Test fun nineteenCachedReadersWithIndependentPad():Unit=runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val app=c.applicationContext as TrackApplication
  val p=AudioImporter(c).import(listOf(Uri.fromFile(ImportBenchmark().wav(4,2,24))),"PAD playback") {}.copy(speed=80,master=.015f,loop=true)
  val rendered=PitchRenderer(c).render(p,0,emptyList()) {}.first()
  val tracks=List(19){rendered.copy(id="reader-$it")};val q=p.copy(stems=tracks,speedTracks=tracks.map{it.id})
  var withPadUnderruns=0
  for(usePad in listOf(false,true)) {
   app.engine.load(q);val end=SystemClock.elapsedRealtime()+30000;while(!app.engine.state.value.ready || app.engine.state.value.projectId!=q.id || app.engine.state.value.preparing){check(SystemClock.elapsedRealtime()<end);Thread.sleep(20)}
   if(usePad)app.pad.play();app.engine.play();Thread.sleep(4000)
   val state=app.engine.state.value;assertTrue(state.playing);assertNull(state.error);assertEquals(19,state.peaks.size)
   android.util.Log.i("DSP13","19 independent cached readers (same synthetic 4s stereo PCM24 source) speed80 pad=$usePad: underruns=${state.underruns} starvation=${state.starvation}")
   if(usePad){withPadUnderruns=state.underruns;assertTrue(app.pad.state.value.playing);assertNull(app.pad.state.value.error)}
   app.engine.stop();app.pad.stop();app.engine.unload();Thread.sleep(300)
  }
  assertEquals(0,withPadUnderruns)
 }
}
