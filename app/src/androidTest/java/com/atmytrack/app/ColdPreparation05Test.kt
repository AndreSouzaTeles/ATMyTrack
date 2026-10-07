package com.atmytrack.app

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ColdPreparation05Test {
    @Test fun nineteenColdPreparationAndValidatedReopen()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val engine=(context.applicationContext as TrackApplication).engine
        val files=File(context.getExternalFilesDir(null),"session-0.5").listFiles()!!.filter { it.extension!="json" }.sortedBy { it.name }
        val report=File(context.getExternalFilesDir(null),"cold-preparation-0.5.txt")
        val started=SystemClock.elapsedRealtime()
        val metadata=AudioImporter(context).import(files.map(Uri::fromFile),"Cold preparation") {}
        val registered=SystemClock.elapsedRealtime()
        // Force fresh cache keys without deleting caches used by saved projects.
        val nonce=UUID.randomUUID().toString()
        val p=metadata.copy(stems=metadata.stems.map { it.copy(fingerprint=it.fingerprint+nonce) })
        report.writeText("19 distinct 330s stems; fresh cache keys, unchanged source bytes; metadata=${registered-started}ms\n")
        engine.load(p)
        val limit=SystemClock.elapsedRealtime()+300000
        var last=-1
        while(!engine.state.value.ready || engine.state.value.projectId!=p.id) {
            assertNull(engine.state.value.error)
            check(SystemClock.elapsedRealtime()<limit)
            val done=engine.state.value.preparation.count { it.status=="READY" }
            if(done!=last) { report.appendText("prepared=$done/19 elapsed=${SystemClock.elapsedRealtime()-registered}ms\n");last=done }
            Thread.sleep(25)
        }
        report.appendText("coldTotal=${SystemClock.elapsedRealtime()-started}ms enginePrep=${engine.state.value.preparationMs}ms\n")
        val reopen=SystemClock.elapsedRealtime();engine.load(p)
        while(!engine.state.value.ready) { check(SystemClock.elapsedRealtime()<limit);Thread.sleep(10) }
        report.appendText("cachedReopen=${SystemClock.elapsedRealtime()-reopen}ms\n")
        for(s in p.stems) {
            val original=FloatArray(8192);val cached=FloatArray(8192)
            Readers.open(context,s,p.sampleRate,4096).use { it.read(0,4096,original) }
            PlaybackCache(context).open(s,p.sampleRate).use { it.read(0,4096,cached) }
            assertArrayEquals("Fresh cache samples ${s.name}",original,cached,0f)
        }
        report.appendText("Fresh cache samples: all 19 stems bit-exact at frame zero.\n")
        engine.play();Thread.sleep(3000)
        assertTrue(engine.state.value.playing);assertEquals(0,engine.state.value.underruns)
        engine.stop();while(engine.state.value.playing)Thread.sleep(10)
        engine.unload();while(engine.state.value.projectId.isNotEmpty())Thread.sleep(10)
    }
}
