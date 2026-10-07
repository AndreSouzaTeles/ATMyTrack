package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

class Layout05Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun transportRemainsFixedWhileProjectsAndMixerScroll() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(30000) { vm.library.value.busy==null }
        val p=vm.library.value.projects.first { it.stems.size==19 && it.name.startsWith("Sessão 19") }
        compose.runOnUiThread { vm.stop();vm.dismissError();vm.dismissImportReport();vm.select(p) }
        compose.waitUntil(180000) { vm.playback.value.projectId==p.id && vm.playback.value.ready }
        compose.onNodeWithText("▶ PLAY").assertIsDisplayed()
        val top=compose.onNodeWithText("▶ PLAY").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("Projetos").assertIsDisplayed()
        assertTrue(compose.onNodeWithContentDescription("Projetos").fetchSemanticsNode().boundsInRoot.top>top)
        fun capture(suffix:String) {
            compose.waitForIdle()
            val label=InstrumentationRegistry.getArguments().getString("layout") ?: "tablet"
            val file=File(instrumentation.targetContext.getExternalFilesDir(null),"layout-$label-$suffix.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        }
        capture("top")
        compose.onNodeWithText("▼ PAGE 2 · MIXER").performClick()
        compose.onNodeWithContentDescription("Canais").performScrollTo()
        compose.onNodeWithContentDescription("Volume ${p.stems.first().name}").performScrollTo()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,10000f) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Volume ${p.stems.first().name}").assertIsDisplayed()
        compose.onNodeWithText("▶ PLAY").assertIsDisplayed()
        assertEquals(top,compose.onNodeWithText("▶ PLAY").fetchSemanticsNode().boundsInRoot.top,.5f)
        capture("faders")
    }
}
