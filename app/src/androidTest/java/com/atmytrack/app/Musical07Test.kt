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

class Musical07Test {
 @Test fun distinctMusicalStemsWithSpeedPitchAndMixer()=runBlocking {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val engine=(context.applicationContext as TrackApplication).engine
    fun await(test:()->Boolean) { val end=SystemClock.elapsedRealtime()+180000;while(!test()){check(SystemClock.elapsedRealtime()<end){engine.state.value.toString()};Thread.sleep(25)} }
    val files=File(context.getExternalFilesDir(null),"session-0.5").listFiles()!!.filter { it.extension!="json" }.sortedBy { it.name }
    assertEquals(19,files.size)
    val imported=AudioImporter(context).import(files.map(Uri::fromFile),"Musical 07") {}
    val original=imported.copy(stems=imported.stems.map { it.copy(frames=imported.sampleRate*20L) },master=.15f,loop=true,bpm=120.0)
    val log=File(context.getExternalFilesDir(null),"musical-0.7.txt")
    log.writeText("19 DISTINCT synthetic musical stems: first20s of session0.5; WAV16/24 MP3 M4A AAC FLAC, 44.1/48/96k, mono/stereo; device ${Build.MODEL} API${Build.VERSION.SDK_INT}.\n")
    engine.load(original);await { engine.state.value.ready && engine.state.value.projectId==original.id && !engine.state.value.preparing }
    engine.play();await { engine.state.value.playing }
    for(speed in listOf(80,120)) {
        val pitchIds=original.stems.filterNot { it.name.contains("drum",true)||it.name.contains("click",true)||it.name.contains("guide",true) }.map { it.id }
        val settings=original.copy(speed=speed,semitones=2,pitchTracks=pitchIds)
        val prep=SystemClock.elapsedRealtime()
        val p=settings.copy(stems=PitchRenderer(context).render(settings,2,pitchIds){})
        log.appendText("speed=$speed pitch=+2 selected=${pitchIds.size} preparation=${SystemClock.elapsedRealtime()-prep}ms\n")
        engine.applyPreparedPitch(p);assertTrue(engine.state.value.playing)
        val start=SystemClock.elapsedRealtime();val cpu=Process.getElapsedCpuTime()
        repeat(30) { turn ->
            if(turn%5==0)engine.update(p.copy(stems=p.stems.mapIndexed { i,s -> s.copy(volume=if(i==0).6f else .8f,pan=if(i==1).3f else 0f,mute=i==2 && turn%10==0,solo=i==3 && turn==15) }))
            if(turn in listOf(8,16,24))engine.seek(p.frames*(turn/8)/4)
            Thread.sleep(500);assertTrue(engine.state.value.playing);assertNull(engine.state.value.error)
        }
        val state=engine.state.value
        log.appendText("wall=${SystemClock.elapsedRealtime()-start}ms cpu=${Process.getElapsedCpuTime()-cpu}ms PSS=${Debug.getPss()}KiB underruns=${state.underruns} starvation=${state.starvation} maxRead=${engine.maxReadNanos/1e6}ms\n")
        assertEquals(0,state.underruns)
    }
    engine.stop();await {!engine.state.value.playing};engine.unload();await { engine.state.value.projectId.isBlank() }
 }
}
