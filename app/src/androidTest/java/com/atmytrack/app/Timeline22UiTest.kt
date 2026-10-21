package com.atmytrack.app

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class Timeline22UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun responsiveTransportAndRealWaveformPreserveSeek() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(30000) { runCatching { compose.onAllNodesWithContentDescription("Abrir menu principal").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.waitUntil(30000) { vm.library.value.busy==null }
        val rate=24000;val frames=rate*24
        val data=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray());data.putInt(36+frames*2);data.put("WAVEfmt ".toByteArray());data.putInt(16)
        data.putShort(1);data.putShort(1);data.putInt(rate);data.putInt(rate*2);data.putShort(2);data.putShort(16)
        data.put("data".toByteArray());data.putInt(frames*2)
        repeat(frames) { i ->
            val time=i.toDouble()/rate
            val envelope=when { time<2 -> 0.0;time>21 -> .6*(24-time)/3;else -> .08+.65*(.5+.5*sin(time*1.7)).pow(2) }
            data.putShort((sin(2*PI*220*time)*envelope*32767).toInt().toShort())
        }
        val audio=File(context.cacheDir,"Timeline22.wav").apply { writeBytes(data.array()) }
        val previous=vm.library.value.selected
        compose.runOnUiThread { vm.endCrossMusic();vm.stop();vm.dismissError();vm.import(uris=listOf(Uri.fromFile(audio))) }
        compose.waitUntil(60000) { vm.library.value.selected!=previous && vm.library.value.busy==null && vm.playback.value.ready && vm.library.value.waveform.size==2048 }
        compose.onNodeWithText("FECHAR").performClick()
        compose.runOnUiThread { vm.dismissImportReport();vm.update { it.copy(name="Dinâmica · teste sintético",key="C",keyAnalyzed=true) };vm.seek(0) }
        compose.waitUntil(30000) { vm.library.value.waveform.size==2048 && vm.playback.value.ready && vm.playback.value.projectId==vm.library.value.selected }
        val timeline=compose.onNodeWithContentDescription("Posição da música")
        val play=compose.onNodeWithContentDescription("Reproduzir")
        val timelineBounds=timeline.fetchSemanticsNode().boundsInRoot
        val playBounds=play.fetchSemanticsNode().boundsInRoot
        val tablet=context.resources.configuration.smallestScreenWidthDp>=600
        if(tablet) { assertTrue(playBounds.right<timelineBounds.left);assertTrue(playBounds.width<timelineBounds.width) }
        else assertTrue(timelineBounds.top>=playBounds.bottom)
        if(tablet) {
            compose.onNodeWithContentDescription("Ferramentas").assertDoesNotExist()
            for(label in listOf("TOM","VELOCIDADE","PAD","LOOP","+ SEÇÃO","METRÔNOMO","DCA","BUS","ROUTING","ATUALIZAR SAÍDA","CROSSMUSIC")) {
                val tool=compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
                val add=compose.onNodeWithContentDescription("Importar projeto").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val bounds=tool.fetchSemanticsNode().boundsInRoot
                assertEquals(add.center.y,bounds.center.y,1f)
                assertTrue(bounds.right<=add.left+1f)
            }
        } else {
            compose.onNodeWithContentDescription("Ferramentas").assertIsDisplayed()
            compose.onNodeWithContentDescription("TOM").assertDoesNotExist()
        }
        compose.openTool("TOM");compose.onNodeWithText("Tom / Key").assertIsDisplayed()
        compose.onNodeWithText("FECHAR").performClick()
        compose.openTool("VELOCIDADE");compose.onNodeWithText("VELOCIDADE").assertIsDisplayed()
        compose.onNodeWithText("FECHAR").performClick()
        if(tablet)compose.onNodeWithContentDescription("TOM").performScrollTo()
        val waveform=vm.library.value.waveform
        assertTrue(waveform.take(80).maxOrNull()!!<.01f)
        assertTrue(waveform.maxOrNull()!!-waveform.minOrNull()!!>.1f)
        val total=vm.library.value.current!!.timelineFrames
        timeline.performSemanticsAction(SemanticsActions.SetProgress) { it(total*.25f) }
        compose.waitUntil(5000) { abs(vm.playback.value.frame-total/4)<2048 }
        val before=vm.playback.value.frame
        timeline.performTouchInput { down(Offset(width*.25f,height*.5f));moveTo(Offset(width*.75f,height*.5f),400) }
        compose.waitForIdle();assertEquals(before,vm.playback.value.frame)
        timeline.performTouchInput { up() }
        compose.waitUntil(5000) { abs(vm.playback.value.frame-(total*.75).toLong())<2048 }
        val label=InstrumentationRegistry.getArguments().getString("label") ?: "device"
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"timeline-0.22.1-$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
