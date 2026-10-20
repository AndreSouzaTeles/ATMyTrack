package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

internal fun ComposeContentTestRule.openTool(label:String) {
    onNodeWithContentDescription("Ferramentas").performClick()
    onNodeWithContentDescription(label).performClick()
}
internal fun ComposeContentTestRule.closeImportedProjectEditor() {
    if(onAllNodesWithText("Editar projeto").fetchSemanticsNodes().isNotEmpty())onNodeWithText("FECHAR").performClick()
}

internal fun ComposeContentTestRule.inspectTools() {
    onNodeWithContentDescription("Ferramentas").performClick()
    onNodeWithContentDescription("CROSSMUSIC").assertExists()
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    waitForIdle()
}
