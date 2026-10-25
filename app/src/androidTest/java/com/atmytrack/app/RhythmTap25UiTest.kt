package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RhythmTap25UiTest {
    @get:Rule(order=0) val notifications=object:org.junit.rules.ExternalResource() {
        override fun before() {
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant com.atmytrack.app android.permission.POST_NOTIFICATIONS")).use {it.readBytes()}
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),name)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.takeScreenshot().let {bitmap->file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name")).use {check(it.readBytes().toString(Charsets.UTF_8).isBlank())}
    }
    @Test fun visibleMetronomeFlagTapAndButtonOrder() {
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(60000) {vm.library.value.busy==null && vm.library.value.background==null}
        compose.waitUntil(30000) {runCatching {compose.onAllNodesWithContentDescription("Abrir menu principal").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        compose.closeImportedProjectEditor()
        compose.runOnUiThread {vm.stop();vm.rhythm.stopAll();vm.rhythm.setLinked(true);if(vm.library.value.current!=null)vm.update {it.copy(bpm=83.0,click=false,clickFollow=false)} else vm.rhythm.configureMetronome {it.copy(bpm=83.0,click=false,clickFollow=false)}}
        compose.waitUntil(10000) {vm.rhythm.state.value.percussion.bpm==83.0}
        compose.waitForIdle()
        val menu=compose.onAllNodesWithContentDescription("Ferramentas").fetchSemanticsNodes().isNotEmpty()
        if(menu)compose.onNodeWithContentDescription("Ferramentas").performClick()
        else compose.onNodeWithContentDescription("METRÔNOMO").performScrollTo()
        val m=compose.onNodeWithContentDescription("METRÔNOMO").fetchSemanticsNode().boundsInRoot
        val p=compose.onNodeWithContentDescription("PERCUSSÃO").fetchSemanticsNode().boundsInRoot
        assertEquals(m.center.y,p.center.y,1f);assertTrue(m.right<=p.left)
        compose.onNodeWithContentDescription("METRÔNOMO").performClick()
        compose.onNodeWithText("Sincronizar com percussão").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sincronizar com percussão").assertIsOn()
        screenshot("metronome-sync-0.25.1.png")
        compose.onNodeWithText("FECHAR").performClick();compose.openTool("PERCUSSÃO")
        compose.onNodeWithContentDescription("Sincronizar com metrônomo").assertIsOn()
        compose.onNodeWithText("TAP BPM").performScrollTo().performClick();Thread.sleep(500)
        compose.onNodeWithText("TAP BPM").performClick()
        compose.waitUntil(10000) {kotlin.math.abs(vm.rhythm.state.value.percussion.bpm-83.0)>1 && kotlin.math.abs(vm.rhythm.state.value.percussion.bpm-vm.rhythm.state.value.metronome.bpm)<.001}
        compose.waitForIdle()
        assertTrue(vm.rhythm.state.value.percussion.bpm in 30.0..300.0)
        screenshot("percussion-tap-0.25.1.png")
        compose.onNodeWithText("FECHAR").performClick();compose.openTool("METRÔNOMO")
        compose.onNodeWithContentDescription("Sincronizar com percussão").assertIsOn()
        compose.onNodeWithText("FECHAR").performClick()
    }
}
