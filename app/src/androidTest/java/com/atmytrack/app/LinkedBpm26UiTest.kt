package com.atmytrack.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class LinkedBpm26UiTest {
    @get:Rule(order=0) val notifications=object:org.junit.rules.ExternalResource() {
        override fun before() {val a=InstrumentationRegistry.getInstrumentation().uiAutomation;android.os.ParcelFileDescriptor.AutoCloseInputStream(a.executeShellCommand("pm grant com.atmytrack.app android.permission.POST_NOTIFICATIONS")).use {it.readBytes()}}
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val file=File(instrumentation.targetContext.getExternalFilesDir(null),name)
        instrumentation.uiAutomation.takeScreenshot().let {bitmap->file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name")).use {check(it.readBytes().toString(Charsets.UTF_8).isBlank())}
    }
    @Test fun percussionTempoIsDisplayedInMetronomeWithLoadedProject() {
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(60000) {vm.library.value.busy==null}
        compose.waitUntil(30000) {runCatching {compose.onAllNodesWithContentDescription("Abrir menu principal").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        compose.closeImportedProjectEditor()
        if(vm.library.value.current==null) {
            val data=ByteBuffer.allocate(44+96000*2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray());data.putInt(data.capacity()-8);data.put("WAVEfmt ".toByteArray());data.putInt(16);data.putShort(1);data.putShort(1);data.putInt(48000);data.putInt(96000);data.putShort(2);data.putShort(16);data.put("data".toByteArray());data.putInt(96000*2)
            repeat(96000){data.putShort((kotlin.math.sin(it*2*Math.PI*220/48000)*2000).toInt().toShort())}
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val fixture=File(context.cacheDir,"LinkedBpm26.wav").apply {writeBytes(data.array())}
            compose.runOnUiThread {vm.import(uris=listOf(Uri.fromFile(fixture)))}
            compose.waitUntil(60000) {vm.library.value.busy==null && vm.library.value.current!=null && vm.playback.value.ready}
            compose.closeImportedProjectEditor()
        }
        compose.runOnUiThread {vm.stop();vm.rhythm.stopAll();vm.rhythm.setLinked(true);vm.update {it.copy(bpm=83.0,click=false,clickFollow=false)}}
        compose.waitUntil(10000) {vm.rhythm.state.value.percussion.bpm==83.0}
        compose.openTool("PERCUSSÃO")
        compose.onNodeWithContentDescription("BPM da percussão").performScrollTo().performTextReplacement("137.0")
        compose.onNodeWithText("APLICAR BPM").performScrollTo().performClick()
        compose.waitUntil(10000) {vm.library.value.current!!.bpm==137.0 && vm.rhythm.state.value.metronome.bpm==137.0}
        compose.onNodeWithText("FECHAR").performClick();compose.openTool("METRÔNOMO")
        compose.onNodeWithContentDescription("BPM do metrônomo").assertTextContains("137.0")
        screenshot("linked-bpm-metronome-0.25.2.png")
        compose.onNodeWithText("FECHAR").performClick();assertEquals(137.0,vm.library.value.current!!.bpm,0.0)
        compose.openTool("PERCUSSÃO");compose.onNodeWithText("TAP BPM").performScrollTo().performClick();Thread.sleep(500);compose.onNodeWithText("TAP BPM").performClick()
        compose.waitUntil(10000) {kotlin.math.abs(vm.library.value.current!!.bpm-137.0)>1 && vm.library.value.current!!.bpm==vm.rhythm.state.value.percussion.bpm}
        val tapped=String.format(Locale.US,"%.1f",vm.library.value.current!!.bpm)
        compose.onNodeWithText("FECHAR").performClick();compose.openTool("METRÔNOMO")
        compose.onNodeWithContentDescription("BPM do metrônomo").assertTextContains(tapped)
        compose.onNodeWithContentDescription("BPM do metrônomo").performTextReplacement("119.0")
        compose.onNodeWithText("FECHAR").performClick();compose.openTool("PERCUSSÃO")
        compose.onNodeWithContentDescription("BPM da percussão").assertTextContains("119.0")
        screenshot("linked-bpm-percussion-0.25.2.png")
        compose.onNodeWithText("FECHAR").performClick()
    }
}
