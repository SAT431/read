package com.example.read

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.assistant.*
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.assistant.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun providerFailureOffersRetryAndHidesItWhileRequesting() {
        val state = mutableStateOf(AssistantState(status = AssistantStatus.FAILED,
            error = "Gemini temporarily unavailable (HTTP 503)", retryUtterance = "Open Botany"))
        var retries = 0
        compose.setContent { FolioTheme(false) {
            AssistantStatusBar(state.value, {}, { retries++; state.value = state.value.copy(
                status = AssistantStatus.REQUESTING, retryUtterance = null) })
        } }
        compose.onNodeWithText("Retry command").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Retry command").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
    @Test fun reviewBringsRetainedTranscriptIntoViewFromSettingsOnSameTab() {
        val review = mutableStateOf(0L)
        var sent = false
        compose.setContent { FolioTheme(false) {
            AssistantScreen(AssistantState(messages = (1..20).map {
                AssistantMessage("m$it", MessageRole.ASSISTANT, "Earlier message $it")
            }), "gemini-3.8-flash", false, null, { _, _, _ -> }, {}, {}, {}, true, review.value) {
                VoiceInputPanel(true, false, false, "Open Botany", null, {}, {}, {}, {}, { sent = true }, true)
            }
        } }
        compose.onNodeWithTag("assistant-list").performScrollToNode(hasText("Your AI API keys"))
        compose.onNodeWithText("Your AI API keys").assertIsDisplayed()
        compose.runOnIdle { review.value++ }
        compose.onNodeWithTag("voice-transcript").assertIsDisplayed()
        compose.onNodeWithTag("voice-send").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, sent) }
    }
}
