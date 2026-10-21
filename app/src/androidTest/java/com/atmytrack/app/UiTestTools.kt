package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

internal fun ComposeContentTestRule.openTool(label:String) {
    if(onAllNodesWithContentDescription("Ferramentas").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithContentDescription("Ferramentas").performClick()
        onNodeWithContentDescription(label).performClick()
    } else onNodeWithContentDescription(label).performScrollTo().performClick()
}
internal fun ComposeContentTestRule.closeImportedProjectEditor() {
    if(onAllNodesWithText("Editar projeto").fetchSemanticsNodes().isNotEmpty())onNodeWithText("FECHAR").performClick()
}

internal fun ComposeContentTestRule.inspectTools() {
    if(onAllNodesWithContentDescription("Ferramentas").fetchSemanticsNodes().isEmpty()) {
        onNodeWithContentDescription("CROSSMUSIC").performScrollTo().assertIsDisplayed()
        return
    }
    onNodeWithContentDescription("Ferramentas").performClick()
    onNodeWithContentDescription("CROSSMUSIC").assertExists()
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    waitForIdle()
}
