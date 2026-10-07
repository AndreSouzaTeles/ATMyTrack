package com.atmytrack.app

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.abs

class CacheAlignment05Test {
    @Test fun preparedFramesMatchOriginalAtEarlyAndLateSeeks()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val files=File(context.getExternalFilesDir(null),"session-0.5").listFiles()!!.filter { it.extension!="json" }.sortedBy { it.name }
        val p=AudioImporter(context).import(files.map(Uri::fromFile),"Alignment") {}
        val report=File(context.getExternalFilesDir(null),"alignment-0.5.txt").apply { writeText("") }
        for(s in p.stems) {
            val a=FloatArray(8192);val b=FloatArray(8192)
            Readers.open(context,s,p.sampleRate,4096).use { original ->
                PlaybackCache(context).open(s,p.sampleRate).use { prepared ->
                    var maxError=0f;var maxSeek=0L;var decoded=0L
                    for(second in listOf(30,60,120,180,300)) {
                        val frame=second*p.sampleRate.toLong()
                        // A continuous decode is the reference. Codec seeks can
                        // produce different predictor/preroll samples (especially
                        // raw AAC); playback caches deliberately avoid those seeks.
                        while(decoded<frame) { val n=minOf(4096L,frame-decoded).toInt();original.read(decoded,n,a);decoded+=n }
                        original.read(frame,4096,a);decoded=frame+4096
                        val started=SystemClock.elapsedRealtime();prepared.read(frame,4096,b);maxSeek=maxOf(maxSeek,SystemClock.elapsedRealtime()-started)
                        repeat(8192) { maxError=maxOf(maxError,abs(a[it]-b[it])) }
                    }
                    report.appendText("${s.name}: native=${s.sourceRate}Hz target=${p.sampleRate}Hz frames=${s.frames}, max seek=${maxSeek}ms sample error=$maxError\n")
                    assertTrue("Cache alignment ${s.name}: $maxError",maxError<.002f)
                }
            }
        }
    }
}
