package com.atmytrack.app
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.*
import org.junit.Assert.*
class PadLocks13Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun individualAndMasterLocksBlockGainsAndPadSurvivesPlayerStop() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(45000) { vm.library.value.current!=null && vm.playback.value.ready }
    compose.runOnUiThread { vm.pad.configure(note=0,minor=false);vm.stop();vm.dismissError();vm.dismissImportReport();vm.update { it.copy(masterFaderLock=false,lockedFaders=emptyList()) } }
    val p=vm.library.value.current!!;val s=p.stems.first()
    compose.runOnUiThread { vm.lockFader(s.id);vm.stem(s.id) { it.copy(volume=.1f) } }
    assertEquals(s.volume,vm.library.value.current!!.stems.first().volume,0f)
    compose.runOnUiThread { vm.lockFader("MASTER");vm.update { it.copy(master=.1f,stems=it.stems.map { x -> x.copy(volume=.2f) }) } }
    assertEquals(p.master,vm.library.value.current!!.master,0f);assertEquals(p.stems.map { it.volume },vm.library.value.current!!.stems.map { it.volume })
    compose.runOnUiThread { vm.lockFader("MASTER") }
    assertTrue(vm.library.value.current!!.gainLocked(s.id))
    compose.onNodeWithText("PAD",useUnmergedTree=true).performScrollTo().performClick()
    compose.onNodeWithText("▶ PLAY PAD").performClick()
    compose.waitUntil(5000) { vm.pad.state.value.playing }
    compose.runOnUiThread { vm.stop() }
    compose.waitForIdle();assertTrue(vm.pad.state.value.playing);assertNull(vm.pad.state.value.error)
    compose.onNodeWithText("Tom: C").performClick();compose.onNodeWithText("F#",useUnmergedTree=true).performClick()
    compose.onNodeWithText("Modo: MAIOR").performClick();compose.onNodeWithText("MENOR",useUnmergedTree=true).performClick()
    assertEquals(6,vm.pad.state.value.note);assertTrue(vm.pad.state.value.minor)
    compose.waitForIdle()
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,java.io.File(compose.activity.getExternalFilesDir(null),"pad-0.13.0.png").outputStream())
    compose.onNodeWithText("■ STOP PAD").performClick();assertFalse(vm.pad.state.value.playing)
 }
}
