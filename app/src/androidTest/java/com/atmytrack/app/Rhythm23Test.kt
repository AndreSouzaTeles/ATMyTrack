package com.atmytrack.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class Rhythm23Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun vm()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun waitFor(condition:()->Boolean)=compose.waitUntil(60000,condition)
    private fun wav(rate:Int=48000):Uri {
        val frames=rate*2;val data=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray());data.putInt(36+frames*2);data.put("WAVEfmt ".toByteArray());data.putInt(16);data.putShort(1);data.putShort(1)
        data.putInt(rate);data.putInt(rate*2);data.putShort(2);data.putShort(16);data.put("data".toByteArray());data.putInt(frames*2)
        repeat(frames) { data.putShort((sin(2*PI*220*it/rate)*5000).toInt().toShort()) }
        return Uri.fromFile(File(context.cacheDir,"Percussao23-$rate-${System.nanoTime()}.wav").apply { writeBytes(data.array()) })
    }
    private fun screenshot(name:String) {
        compose.waitForIdle();Thread.sleep(300);val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    @After fun stopRhythms() { compose.runOnUiThread { vm().rhythm.stopAll();vm().pause();vm().update { it.copy(click=false,clickFollow=true) } } }
    @Test fun standaloneMetronomeNeedsNoProjectAndDoesNotAutostartAfterReload() {
        val engine=RhythmEngine(context)
        try {
            engine.configureMetronome { it.copy(click=true,clickFollow=false,bpm=120.0) }
            engine.armMetronome(true);engine.audioFocus(true)
            waitFor { engine.renderedIndependentFrames>8192 }
            assertTrue(engine.state.value.metronome.stems.isEmpty())
            assertTrue(engine.state.value.independentWanted);assertNull(engine.state.value.error)
            engine.stopAll();assertFalse(engine.state.value.independentWanted)
            val restored=RhythmEngine(context)
            try { assertFalse(restored.state.value.metronome.click);assertFalse(restored.state.value.metronomeArmed) }
            finally { restored.close() }
        } finally { engine.close() }
    }
    @Test fun independentSoundsKeepMusicStoppedAndFollowModeUsesMixerClock() {
        val vm=vm();waitFor { vm.library.value.busy==null }
        compose.waitUntil(30000) { runCatching { compose.onAllNodesWithContentDescription("Abrir menu principal").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        if(vm.library.value.current==null) {
            compose.runOnUiThread { vm.import(uris=listOf(wav())) }
            waitFor { vm.library.value.busy==null && vm.playback.value.ready };compose.closeImportedProjectEditor()
        }
        waitFor { vm.playback.value.ready && vm.library.value.background==null }
        compose.runOnUiThread { vm.rhythm.stopAll();vm.stop();vm.dismissError();vm.dismissImportReport();vm.update { it.copy(click=false,clickFollow=true) } }
        waitFor { !vm.playback.value.playing }
        if(compose.onAllNodesWithContentDescription("Ferramentas").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Ferramentas").performClick()
            val pad=compose.onNodeWithContentDescription("PAD").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val percussion=compose.onNodeWithContentDescription("PERCUSSÃO").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(pad.center.y,percussion.center.y,1f);assertTrue(percussion.left>=pad.right)
            screenshot("tools-rhythm-0.23.0.png")
            compose.onNodeWithContentDescription("METRÔNOMO").performClick()
        } else {
            compose.onNodeWithContentDescription("PAD").performScrollTo()
            compose.onNodeWithContentDescription("PERCUSSÃO").performScrollTo().assertIsDisplayed()
            screenshot("tools-rhythm-0.23.0.png")
            compose.openTool("METRÔNOMO")
        }
        compose.onNodeWithContentDescription("Acompanhar música do metrônomo").assertIsOn().performClick()
        val before=vm.rhythm.renderedIndependentFrames
        compose.onNodeWithContentDescription("Reprodução do metrônomo").performClick()
        waitFor { vm.rhythm.renderedIndependentFrames>before+4096 }
        assertFalse(vm.playback.value.playing)
        compose.runOnUiThread { vm.pause();vm.stop() }
        val afterStop=vm.rhythm.renderedIndependentFrames
        waitFor { vm.rhythm.renderedIndependentFrames>afterStop+8192 }
        assertFalse(vm.playback.value.playing)
        screenshot("metronome-0.23.0.png")
        compose.onNodeWithContentDescription("Reprodução do metrônomo").performClick();compose.onNodeWithText("FECHAR").performClick()
        compose.openTool("PERCUSSÃO")
        compose.runOnUiThread { vm.rhythm.configure { it.copy(source="Funk",follow=false,bpm=120.0,enabled=false) } }
        waitFor { !vm.rhythm.state.value.preparing }
        compose.onNodeWithContentDescription("Reprodução da percussão").performClick()
        val percussionStart=vm.rhythm.renderedIndependentFrames
        waitFor { vm.rhythm.renderedIndependentFrames>percussionStart+8192 };assertFalse(vm.playback.value.playing)
        screenshot("percussion-independent-0.23.0.png")
        compose.onNodeWithContentDescription("Acompanhar música da percussão").performScrollTo().performClick()
        waitFor { !vm.rhythm.state.value.preparing }
        val p=vm.library.value.current!!.engineView()
        val whole=FloatArray(1024);vm.rhythm.mixFollow(whole,512,0,p)
        assertTrue(whole.any { abs(it)>.001f })
        val a=FloatArray(256);val b=FloatArray(768)
        vm.rhythm.mixFollow(a,128,0,p);vm.rhythm.mixFollow(b,384,128,p)
        assertArrayEquals(whole,a+b,0f)
        val unrelated=FloatArray(1024);vm.rhythm.mixFollow(unrelated,512,0,p.copy(id="another-project"));assertTrue(unrelated.all { it==0f })
        screenshot("percussion-0.23.0.png")
        compose.onNodeWithText("FECHAR").performClick()
        compose.runOnUiThread { vm.play() };waitFor { vm.playback.value.playing }
        compose.runOnUiThread { vm.pause() };waitFor { !vm.playback.value.playing }
        assertNull(vm.playback.value.error)
        compose.runOnUiThread { vm.stop() }
        compose.waitForIdle();Thread.sleep(300)
        assertTrue(vm.rhythm.state.value.percussion.enabled && vm.rhythm.state.value.percussion.follow)
        compose.runOnUiThread { vm.play() };waitFor { vm.playback.value.playing }
    }
    @Test fun importedLoopsStretchPreservePitchAndSurviveReloadWithoutChangingProjects()=runBlocking {
        val vm=vm();waitFor { vm.library.value.busy==null }
        val projects=vm.library.value.projects
        val engine=RhythmEngine(context)
        try {
            waitFor { !engine.state.value.preparing }
            val old=engine.state.value.imports.size
            engine.importLoop(wav(44100))
            waitFor { !engine.state.value.loading && !engine.state.value.preparing }
            assertNull(engine.state.value.error);assertEquals(old+1,engine.state.value.imports.size)
            val imported=engine.state.value.imports.last()
            assertEquals(96000,imported.frames)
            engine.configure { it.copy(bpm=80.0,bars=1,follow=false) }
            waitFor { !engine.state.value.preparing };assertNull(engine.state.value.error)
            val source=FloatArray(96000*2) { i -> (.2*sin(2*PI*220*(i/2)/48000)).toFloat() }
            val stretched=PercussionLoopStretch.render(source,144000)
            assertEquals(288000,stretched.size)
            val crossings=(48001 until 96000).count { stretched[it*2]>0 && stretched[(it-1)*2]<=0 }
            assertEquals(220.0,crossings.toDouble(),5.0)
            assertEquals(projects,vm.library.value.projects)
            val restored=RhythmEngine(context)
            try {
                waitFor { !restored.state.value.preparing }
                assertNull(restored.state.value.error);assertTrue(restored.state.value.imports.any { it.id==imported.id })
                assertEquals(imported.id,restored.state.value.percussion.source)
                assertFalse(restored.state.value.percussion.enabled)
                restored.configure { it.copy(enabled=true,follow=false) };restored.audioFocus(true)
                waitFor { restored.renderedIndependentFrames>8192 };assertFalse(vm.playback.value.playing)
                val music=Project(id="rhythm-import-test",name="Clock",sampleRate=48000,stems=emptyList(),bpm=80.0)
                restored.syncProject(music);restored.configure { it.copy(follow=true,enabled=true) }
                waitFor { !restored.state.value.preparing }
                val actual=FloatArray(96000);restored.mixFollow(actual,48000,48000,music)
                val actualCrossings=(1 until 48000).count { actual[it*2]>0 && actual[(it-1)*2]<=0 }
                assertEquals(220.0,actualCrossings.toDouble(),6.0)
            } finally { restored.close() }
        } finally { engine.close() }
    }
}
