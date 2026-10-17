package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.assistant.AssistantComposer
import dev.folio.designsystem.theme.FolioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AssistantUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun unconfiguredServiceDoesNotSend() {
        var sent: String? = null
        compose.setContent { FolioTheme(false) { AssistantComposer(false, false, false, { sent = it }, {}) } }
        compose.onNodeWithTag("assistant-input").performTextInput("Open Botany")
        compose.onNodeWithTag("assistant-send").assertIsNotEnabled()
        assertEquals(null, sent)
    }
    @Test fun configuredComposerSendsExactlyUserUtterance() {
        var sent: String? = null
        compose.setContent { FolioTheme(false) { AssistantComposer(true, false, false, { sent = it }, {}) } }
        compose.onNodeWithTag("assistant-input").performTextInput("Open Botany")
        compose.onNodeWithTag("assistant-send").performClick()
        assertEquals("Open Botany", sent)
    }
}
