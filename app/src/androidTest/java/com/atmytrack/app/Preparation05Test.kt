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

class Preparation05Test {
    @Test fun preparationFailureBlocksPlayAndRemovingBadTrackRecovers()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val engine=(context.applicationContext as TrackApplication).engine
        val good=AudioImporter(context).import(listOf(Uri.fromFile(File(context.getExternalFilesDir(null),"fixtures/Aligned.wav"))),"Recovery") {}
        val broken=Stem(name="Arquivo removido",pcm="missing.pcm",frames=48000)
        engine.load(good.copy(stems=good.stems+broken))
        await { !engine.state.value.preparing }
        assertFalse(engine.state.value.ready)
        assertTrue(engine.state.value.preparation.any { it.id==broken.id && it.status=="ERROR" })
        engine.play();Thread.sleep(100);assertFalse(engine.state.value.playing)
        engine.load(good);await { engine.state.value.ready && engine.state.value.projectId==good.id }
        assertTrue(engine.state.value.preparation.all { it.status=="READY" })
        engine.play();await { engine.state.value.frame>24000 }
        engine.stop();await { !engine.state.value.playing };engine.unload();await { engine.state.value.projectId.isEmpty() }
    }
    @Test fun cacheReuseAndCancellationNeverPublishPartialAudio()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val p=AudioImporter(context).import(listOf(Uri.fromFile(File(context.getExternalFilesDir(null),"fixtures/Aligned.m4a"))),"Cache") {}
        val cache=PlaybackCache(context)
        val root=File(context.filesDir,"playback")
        val s=p.stems.single().copy(fingerprint=p.stems.single().fingerprint+"-cancellation-test")
        var calls=0
        try { cache.open(s,p.sampleRate,{ if(++calls>10)error("Cancelled by test") });fail("Must cancel") } catch(e:IllegalStateException) { assertEquals("Cancelled by test",e.message) }
        assertTrue(root.listFiles().orEmpty().none { it.extension=="part" })
        cache.open(p.stems.single(),p.sampleRate).close()
        val before=root.listFiles().orEmpty().associate { it.name to (it.length() to it.lastModified()) }
        cache.open(p.stems.single(),p.sampleRate).use { it.read(48000L*170,512,FloatArray(1024)) }
        val after=root.listFiles().orEmpty().associate { it.name to (it.length() to it.lastModified()) }
        assertEquals(before,after)
    }
    private fun await(predicate:()->Boolean) { val end=SystemClock.elapsedRealtime()+180000;while(!predicate()) { check(SystemClock.elapsedRealtime()<end);Thread.sleep(20) } }
}
