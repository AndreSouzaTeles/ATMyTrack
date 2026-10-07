package com.atmytrack.app

import android.net.Uri
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class Profile05Test {
    @Test fun progressiveProfile()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val args=InstrumentationRegistry.getArguments()
        val label=args.getString("label") ?: "baseline"
        val duration=args.getString("duration")?.toLong() ?: 12000L
        val fixtures=File(context.getExternalFilesDir(null),args.getString("fixtures") ?: "fixtures").listFiles()!!.filter { it.extension in listOf("wav","mp3","m4a","aac","flac","ogg","opus") }.sortedBy { it.name }
        val engine=(context.applicationContext as TrackApplication).engine
        val log=File(context.getExternalFilesDir(null),"profile-$label.txt")
        log.writeText("API ${Build.VERSION.SDK_INT} ${Build.MODEL}; each duration=$duration ms; CPU=process ms/wall ms, 100%=one core\n")
        for(n in listOf(1,2,4,8,12,19)) {
            val start=SystemClock.elapsedRealtime()
            val p=AudioImporter(context).import(List(n){Uri.fromFile(fixtures[it%fixtures.size])},"Profile $n") {}
            val metadata=SystemClock.elapsedRealtime()-start
            engine.load(p)
            await { engine.state.value.projectId==p.id }
            val ready=SystemClock.elapsedRealtime()-start
            val playDuration=if(args.getString("longTwo")=="true" && n==2)120000L else duration
            val cpu=Process.getElapsedCpuTime();val gc=Debug.getRuntimeStat("art.gc.gc-count")?.toLong() ?: 0L
            engine.play();await { engine.state.value.playing }
            Thread.sleep(playDuration)
            val state=engine.state.value
            val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
            log.appendText("n=$n metadata=${metadata}ms ready=${ready}ms wall=${playDuration}ms cpu=${Process.getElapsedCpuTime()-cpu}ms gc=${(Debug.getRuntimeStat("art.gc.gc-count")?.toLong() ?: 0L)-gc} pss=${info.totalPss}KiB underruns=${state.underruns} starvation=${state.starvation} producerWaits=${state.producerWaits} readMax=${engine.maxReadNanos/1000000.0}ms clippedBlocks=${engine.clippedBlocks} frame=${state.frame} error=${state.error}\n")
            assertNull(state.error);assertTrue(state.playing)
            engine.stop();await { !engine.state.value.playing };engine.unload();await { engine.state.value.projectId.isEmpty() }
        }
    }
    private fun await(condition:()->Boolean) { val end=SystemClock.elapsedRealtime()+180000;while(!condition()) { check(SystemClock.elapsedRealtime()<end);Thread.sleep(20) } }
}
