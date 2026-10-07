package com.atmytrack.app

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class LoopControl05Test {
    @Test fun enableLoopWhenProducerHasAlreadyReachedEnd()=exercise(false)
    @Test fun disableLoopWhenLookaheadHasAlreadyWrapped()=exercise(true)
    private fun exercise(initialLoop:Boolean)=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val p=AudioImporter(context).import(listOf(Uri.fromFile(ImportBenchmark().wav(2,1,16))),"Loop boundary") {}.copy(loop=initialLoop)
        val engine=(context.applicationContext as TrackApplication).engine
        try {
            engine.load(p);await { engine.state.value.ready && engine.state.value.projectId==p.id }
            engine.play();await { engine.state.value.playing && engine.state.value.frame>p.frames-7000 }
            engine.update(p.copy(loop=!initialLoop))
            Thread.sleep(500)
            assertNull(engine.state.value.error)
            if(initialLoop) {
                assertFalse("Loop off must stop at the audible cycle boundary",engine.state.value.playing)
                assertEquals(p.frames,engine.state.value.frame)
            } else {
                assertTrue("Enabling loop during drain must resume production",engine.state.value.playing)
                assertTrue(engine.state.value.frame<48000)
            }
            assertEquals(0,engine.state.value.underruns)
        } finally { engine.stop();await { !engine.state.value.playing };engine.unload();await { engine.state.value.projectId.isEmpty() } }
    }
    private fun await(predicate:()->Boolean) { val end=SystemClock.elapsedRealtime()+15000;while(!predicate()) { check(SystemClock.elapsedRealtime()<end);Thread.sleep(5) } }
}
