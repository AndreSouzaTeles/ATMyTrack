package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.data.ProjectStore
import org.junit.*
import org.junit.Assert.*

class Routing12Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun routingPresetsAndMasterPanPersist() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000) { vm.library.value.current!=null && vm.playback.value.ready }
    compose.runOnUiThread { vm.stop();vm.dismissError();vm.dismissImportReport() }
    compose.onNodeWithText("Mixer",useUnmergedTree=true).performClick()
    compose.onNodeWithText("ROUTING").performScrollTo().performClick()
    compose.onNodeWithContentDescription("STEREO SPLIT").performScrollTo().performClick()
    compose.onNodeWithContentDescription("STEREO SPLIT").assertIsSelected()
    compose.onNodeWithContentDescription("CLICK INTERNO").assertIsNotSelected()
    compose.onNodeWithContentDescription("BOTH ALL").assertIsNotSelected()
    compose.runOnUiThread { vm.update { it.copy(clickRoute="RIGHT") } }
    compose.onNodeWithContentDescription("STEREO SPLIT").assertIsNotSelected()
    assertTrue(vm.library.value.current!!.stems.all { it.route in listOf("LEFT","RIGHT") && it.bus.isEmpty() })
    compose.onNodeWithContentDescription("BOTH ALL").performScrollTo().performClick()
    assertTrue(vm.library.value.current!!.stems.all { it.route=="BOTH" && it.bus.isEmpty() })
    assertEquals("BOTH",vm.library.value.current!!.clickRoute)
    compose.onNodeWithContentDescription("BOTH ALL").assertIsSelected()
    compose.onNodeWithContentDescription("STEREO SPLIT").assertIsNotSelected()
    compose.onNodeWithContentDescription("CLICK INTERNO").assertIsNotSelected()
    val tops=listOf("CLICK INTERNO","STEREO SPLIT","BOTH ALL").map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.top }
    assertTrue(tops.max()-tops.min()<2f)
    compose.onNodeWithText("FECHAR").performClick()
    compose.onNodeWithContentDescription("Pan MASTER").performSemanticsAction(SemanticsActions.SetProgress) { it(.65f) }
    assertEquals(.65f,vm.library.value.current!!.masterPan,.01f)
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    compose.waitUntil(5000) { ProjectStore(context.filesDir).load().find { it.id==vm.library.value.selected }?.masterPan==vm.library.value.current!!.masterPan }
 }
}
