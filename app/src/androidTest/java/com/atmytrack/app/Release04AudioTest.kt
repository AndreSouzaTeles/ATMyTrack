package com.atmytrack.app

import android.net.Uri
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.*

class Release04AudioTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixtures()=File(context.getExternalFilesDir(null),"fixtures").listFiles()!!.filter { it.extension in listOf("wav","mp3","m4a","aac","flac","ogg","opus") }.sortedBy { it.name }
    private fun log(name:String,text:String)=File(context.getExternalFilesDir(null),name.replace("0.4","regression-0.5")).appendText(text+"\n")
    private fun await(condition:()->Boolean) { val limit=SystemClock.elapsedRealtime()+180000;while(!condition()) { check(SystemClock.elapsedRealtime()<limit);Thread.sleep(20) } }
    @Test fun importToPlaybackReadinessWith24Formats()=runBlocking {
        val files=fixtures();val start=SystemClock.elapsedRealtime()
        val p=AudioImporter(context).import(List(24){Uri.fromFile(files[it%files.size])},"Readiness") {}.copy(master=.015f)
        val imported=SystemClock.elapsedRealtime()
        val engine=(context.applicationContext as TrackApplication).engine
        try {
            engine.load(p);await { engine.state.value.projectId==p.id }
            val opened=SystemClock.elapsedRealtime()
            engine.play();await { engine.state.value.frame>=24000 }
            assertTrue(engine.state.value.playing);assertNull(engine.state.value.error)
            log("readiness-0.4.txt","24 mixed 180s stems: metadata=${imported-start}ms; opening readers/output=${opened-imported}ms; play command until playback head >=0.5s=${SystemClock.elapsedRealtime()-opened}ms")
        } finally { engine.stop();await { !engine.state.value.playing };engine.unload();await { engine.state.value.projectId.isEmpty() } }
    }
    @Test fun allFormatsLongFilesAndMixedImportCounts()=runBlocking {
        val sources=fixtures();assertEquals(7,sources.size)
        val failures=mutableListOf<String>()
        for(file in sources) {
            val start=SystemClock.elapsedRealtime()
            val p=AudioImporter(context).import(listOf(Uri.fromFile(file)),file.extension) {}
            val elapsed=SystemClock.elapsedRealtime()-start
            assertEquals(48000,p.sampleRate);assertTrue("Duration ${file.name}: ${p.seconds}",p.seconds in 179.0..181.0)
            val s=p.stems.single();val block=FloatArray(8192)
            val positions=listOf(0L,48000L,90*48000L,178*48000L)
            val expected=mutableMapOf<Long,FloatArray>()
            Readers.open(context,s,p.sampleRate,4096).use { reader ->
                var pos=0L
                for(target in positions) {
                    while(pos<target) { val count=minOf(512L,target-pos).toInt();reader.read(pos,count,block);pos+=count }
                    reader.read(target,4096,block);expected[target]=block.copyOf();pos=target+4096
                }
            }
            var error=0f
            var maxSeekMs=0L
            val seekErrors=mutableListOf<String>()
            Readers.open(context,s,p.sampleRate,4096).use { reader ->
                for(pos in positions.reversed()) {
                    val seekStart=SystemClock.elapsedRealtime();reader.read(pos,4096,block)
                    maxSeekMs=max(maxSeekMs,SystemClock.elapsedRealtime()-seekStart)
                    var at=0f;repeat(8192) { at=max(at,abs(block[it]-expected.getValue(pos)[it])) };error=max(error,at);seekErrors+="$pos=$at"
                }
            }
            // The source repeats each second. Lossy codecs may add constant priming,
            // but their time alignment must not drift between 90 and 178 seconds.
            val middle=expected.getValue(90*48000L);val late=expected.getValue(178*48000L)
            val lag=(-16..16).minBy { shift -> (32 until 4000).sumOf { i -> val d=middle[i*2]-late[(i+shift)*2];(d*d).toDouble() } }
            if(abs(lag)>1)failures+="Accumulated drift ${file.name}: $lag frames"
            log("formats-0.4.txt","${file.name}: ${elapsed}ms, ${p.frames} frames, seek error $error, direct=${s.external}, max seek ${maxSeekMs}ms, drift $lag frames, $seekErrors")
            if(error>=.003f)failures+="Seek ${file.name}: $error $seekErrors"
        }
        for(count in listOf(1,5,10,24)) {
            val start=SystemClock.elapsedRealtime()
            val p=AudioImporter(context).import(List(count){Uri.fromFile(sources[it%sources.size])},"Mixed $count") {}
            assertEquals(count,p.stems.size);assertTrue(p.stems.all { it.external && it.pcm.isEmpty() })
            log("formats-0.4.txt","Mixed $count formats: ${SystemClock.elapsedRealtime()-start}ms; no copies")
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun partialImportReportsErrorAndRetainsGoodTracks()=runBlocking {
        val broken=File(context.cacheDir,"Broken.wav").apply { writeText("broken") }
        val states=java.util.concurrent.ConcurrentHashMap<Int,ImportItem>()
        val p=AudioImporter(context).import(listOf(Uri.fromFile(fixtures().first { it.extension=="wav" }),Uri.fromFile(broken)),"Partial",allowPartial=true,onItem={states[it.index]=it}) {}
        assertEquals(1,p.stems.size);assertEquals("DISCOVERED",states[0]!!.status);assertEquals("ERROR",states[1]!!.status)
        assertTrue(states[1]!!.name.contains("Broken"));assertTrue(states[1]!!.detail.isNotEmpty())
    }
    @Test fun pitchMatrixMultipleSelectionsAndCache()=runBlocking {
        val source=ImportBenchmark().wav(4,1,16)
        val p=AudioImporter(context).import(List(3){Uri.fromFile(source)},"Pitch matrix") {}.copy(bpm=72.0)
        for(shift in listOf(1,2,3,-3,12,-12)) {
            for(selected in listOf(p.stems.take(1),p.stems.take(2),p.stems)) {
                val changed=PitchRenderer(context).render(p,shift,selected.map { it.id }) {}
                for((index,s) in changed.withIndex()) {
                    assertEquals(p.stems[index].frames,s.frames)
                    if(s.id !in selected.map { it.id }) { assertEquals(p.stems[index],s);continue }
                    val block=FloatArray(96000);Readers.open(context,s,48000,48000).use { it.read(48000,48000,block) }
                    var frequency=0;for(i in 1 until 48000)if(block[(i-1)*2]<=0 && block[i*2]>0)frequency++
                    assertEquals(220*2.0.pow(shift/12.0),frequency.toDouble(),2.0)
                }
                val updated=p.copy(stems=changed)
                assertEquals(p.bpm,updated.bpm,0.0);assertEquals(p.frames,updated.frames)
                val cached=PitchRenderer(context).render(p,shift,selected.map { it.id }) { error("Cache unexpectedly rebuilt") }
                assertEquals(changed,cached)
            }
        }
        val reset=PitchRenderer(context).render(p,0,p.stems.map { it.id }) {}
        assertEquals(p.stems,reset)
    }
    @Test fun smartClickAnalyzesClickDrumsAndNoClickWithoutChangingProjectTempo()=runBlocking {
        for(name in listOf("Click","Drums","Guitar")) {
            val file=if(name=="Click")fixtures().first { it.extension=="wav" } else File(context.getExternalFilesDir(null),"fixtures/tempo/$name.wav")
            val imported=AudioImporter(context).import(listOf(Uri.fromFile(file)),"Tempo") {}
            val p=imported.copy(bpm=70.0,stems=imported.stems.map { it.copy(name=name) })
            val result=Analysis(context).tempo(p)
            log("smart-click-0.4.txt","$name: ${result.bpm}, confidence ${result.confidence}")
            assertEquals(120.0,result.bpm,1.0);assertEquals(70.0,p.bpm,0.0)
        }
    }
    @Test fun threeMinuteMixedPlaybackHasOneTimelineAndPitchSwapKeepsOutput()=runBlocking {
        val files=fixtures(); val p=AudioImporter(context).import(List(24){Uri.fromFile(files[it%files.size])},"Soak") {}.copy(master=.015f,loop=true)
        val engine=(context.applicationContext as TrackApplication).engine
        engine.load(p);await { engine.state.value.projectId==p.id };engine.play();await { engine.state.value.frame>48000 }
        val start=SystemClock.elapsedRealtime();var updates=0;var maxUnderruns=0;var steadyUnderruns=0
        val underrunEvents=mutableListOf<String>()
        val before=android.os.Debug.getPss()
        while(SystemClock.elapsedRealtime()-start<181000) {
            val state=engine.state.value;assertTrue("${state.error}",state.playing);assertNull(state.error)
            if(state.peaks.size==24) {
                p.stems.indices.groupBy { p.stems[it].source }.values.forEach { indices ->
                    val first=state.peaks[indices[0]];indices.forEach { assertEquals("Drift between matching decoders",first,state.peaks[it],.0001f) }
                }
            }
            val elapsed=SystemClock.elapsedRealtime()-start
            if(state.underruns>maxUnderruns)underrunEvents+="${elapsed}ms/frame ${state.frame}: ${state.underruns}"
            if(elapsed<175000)steadyUnderruns=max(steadyUnderruns,state.underruns)
            maxUnderruns=max(maxUnderruns,state.underruns);updates++;Thread.sleep(100)
        }
        engine.pause();await { !engine.state.value.playing };engine.seek(48000);await { engine.state.value.frame==48000L };engine.play();await { engine.state.value.frame>60000 }
        val short=ImportBenchmark().wav(4,1,16)
        engine.stop();await { !engine.state.value.playing }
        val pitchProject=AudioImporter(context).import(List(3){Uri.fromFile(short)},"Live pitch") {}.copy(loop=true,master=.1f)
        engine.load(pitchProject);await { engine.state.value.projectId==pitchProject.id };engine.play();await { engine.state.value.frame>10000 }
        val rendered=PitchRenderer(context).render(pitchProject,2,pitchProject.stems.map { it.id }) {}
        val frame=engine.state.value.frame;val underruns=engine.state.value.underruns
        engine.applyPreparedPitch(pitchProject.copy(stems=rendered,semitones=2))
        assertTrue(engine.state.value.playing);assertEquals(underruns,engine.state.value.underruns)
        await { engine.state.value.frame!=frame };assertNull(engine.state.value.error)
        log("soak-0.4.txt","24 mixed stems, 181 seconds, $updates snapshots, steady underruns=$steadyUnderruns, max underruns=$maxUnderruns, events=$underrunEvents, PSS before=${before}KB after=${Debug.getPss()}KB. Loop/pause/seek/pitch reader swap passed.")
        engine.stop();await { !engine.state.value.playing };engine.unload()
        assertEquals("Continuous playback must not underrun before the end/loop boundary",0,steadyUnderruns)
    }
}
