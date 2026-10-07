package com.atmytrack.app

import android.Manifest
import android.net.Uri
import android.os.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlin.math.*

class Session05Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun nineteenIndependentStemsWithLiveUiAndSeek() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        val files=File(context.getExternalFilesDir(null),"session-0.5").listFiles()!!.filter { it.extension!="json" }.sortedBy { it.name }
        assertEquals(19,files.size)
        val report=File(context.getExternalFilesDir(null),"session-${InstrumentationRegistry.getArguments().getString("reportVersion") ?: "0.5"}.txt")
        val started=SystemClock.elapsedRealtime()
        compose.waitUntil(30000) { vm.library.value.busy==null }
        val old=vm.library.value.selected
        compose.runOnUiThread { vm.stop();vm.import(uris=files.map(Uri::fromFile)) }
        compose.waitUntil(30000) { vm.library.value.selected!=old }
        val metadata=SystemClock.elapsedRealtime()-started
        compose.waitUntil(300000) { vm.playback.value.ready && vm.playback.value.projectId==vm.library.value.selected }
        val preparation=SystemClock.elapsedRealtime()-started
        report.writeText("19 distinct synthetic musical stems, WAV16/24, MP3/M4A/AAC/FLAC, mono/stereo, 44.1/48/96kHz, 330s. metadata=$metadata ms totalReady=$preparation ms prepEngine=${vm.playback.value.preparationMs} ms\n")
        assertTrue(vm.playback.value.preparation.all { it.status=="READY" })
        compose.runOnUiThread {
            vm.dismissError();vm.dismissImportReport()
            vm.update { it.copy(name="Sessão 19 · 0.5",bpm=120.0,loop=true,markers=listOf(Marker(name="INTRO",start=0,end=48000L*30),Marker(name="REFRÃO",start=48000L*60,end=48000L*90))) }
        }
        compose.onNodeWithText("▶ PLAY").assertIsEnabled().performClick()
        compose.waitUntil(10000) { vm.playback.value.playing && vm.playback.value.frame>24000 }
        val cpuStart=Process.getElapsedCpuTime();val wallStart=SystemClock.elapsedRealtime()
        val gcStart=Debug.getRuntimeStat("art.gc.gc-count")!!.toLong()
        val pssStart=Debug.getPss()
        val duration=InstrumentationRegistry.getArguments().getString("soakMs")?.toLong() ?: 600000L
        var turn=0;var maxUnderruns=0;var maxStarvation=0L;var maxPss=pssStart
        var page=0
        var previousUnderruns=0
        while(SystemClock.elapsedRealtime()-wallStart<duration) {
            val elapsed=SystemClock.elapsedRealtime()-wallStart
            // Actual UI actions repeatedly exercise layout and gesture dispatch.
            if(turn%10==0) {
                compose.onNodeWithText(if(page==0)"Mixer" else "Playback").performClick();page=1-page
                compose.onNodeWithText("Ⅱ PAUSAR").assertIsDisplayed()
                compose.onNodeWithText("METRÔNOMO").performScrollTo().performClick()
                compose.onNodeWithText("FECHAR").performClick()
                compose.onNodeWithContentDescription("Canais").performScrollTo()
                compose.onNodeWithText("Ⅱ PAUSAR").assertIsDisplayed()
                val name=vm.library.value.current!!.stems[(turn/10)%19].name
                compose.onNodeWithContentDescription("Canais").performScrollToNode(hasContentDescription("Arrastar $name"))
                compose.onNodeWithContentDescription("Mute $name").performClick()
                compose.onNodeWithContentDescription("Mute $name").performClick()
                compose.onNodeWithContentDescription("Solo $name").performClick()
                compose.onNodeWithContentDescription("Solo $name").performClick()
                compose.onNodeWithContentDescription("Pan $name").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(if(turn%20==0)-.4f else .4f) }
                compose.onNodeWithContentDescription("Volume $name").performTouchInput { down(center);moveBy(Offset(0f,-12f),150);up() }
                maxPss=max(maxPss,Debug.getPss())
            }
            if(turn in listOf(2,8,14,20,26)) {
                val second=when(turn) { 2->30;8->60;14->120;20->180;else->0 }
                compose.runOnUiThread { vm.seek(second*48000L) }
                compose.waitUntil(10000) { vm.playback.value.frame in second*48000L..(second+2)*48000L }
                assertTrue(vm.playback.value.playing)
                report.appendText("seek $second at ${elapsed}ms resumed frame=${vm.playback.value.frame}\n")
            }
            val state=vm.playback.value
            assertNull(state.error);assertTrue(state.playing)
            if(state.underruns!=previousUnderruns) { report.appendText("UNDERRUN changed at ${elapsed}ms frame=${state.frame}, value=${state.underruns}, readMax=${(context.applicationContext as TrackApplication).engine.maxReadNanos/1e6}ms\n");previousUnderruns=state.underruns }
            maxUnderruns=max(maxUnderruns,state.underruns);maxStarvation=max(maxStarvation,state.starvation)
            if(turn%30==0)report.appendText("elapsed=${elapsed}ms frame=${state.frame} underruns=${state.underruns} starvation=${state.starvation} producerWaits=${state.producerWaits} pss=${Debug.getPss()}KiB\n")
            Thread.sleep(900);turn++
        }
        val wall=SystemClock.elapsedRealtime()-wallStart
        report.appendText("wall=${wall}ms cpu=${Process.getElapsedCpuTime()-cpuStart}ms gc=${Debug.getRuntimeStat("art.gc.gc-count")!!.toLong()-gcStart} PSS=$pssStart..${maxPss}KiB underruns=$maxUnderruns starvation=$maxStarvation interactions=$turn\n")
        compose.runOnUiThread { vm.stop() }
        compose.waitUntil { !vm.playback.value.playing }
        assertEquals("No underruns in the complete session",0,maxUnderruns)
        // Persist a final screenshot with the fixed transport and blue mixer.
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> File(context.getExternalFilesDir(null),"session-0.5.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    }
}
