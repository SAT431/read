package com.example.read

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.assistant.VoiceInputPanel
import androidx.compose.foundation.layout.Column
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.assistant.VoiceInputState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises prebuilt controls without recording the user's microphone or calling a provider. */
class VoiceUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()

    @Test fun connectionRecordingCancellationAndReviewedSubmission() {
        val connected = mutableStateOf(false)
        val voice = mutableStateOf(VoiceInputState())
        var sent: String? = null
        var stops = 0
        compose.setContent {
            FolioTheme(false) {
                val state = voice.value
                Column {
                VoiceInputPanel(connected.value, state.recording, state.processing, state.transcript, state.error,
                    onStart = { voice.value = VoiceInputState(recording = true) },
                    onFinish = { stops++; voice.value = VoiceInputState(processing = true) },
                    onCancel = { voice.value = VoiceInputState() },
                    onEdit = { voice.value = voice.value.copy(transcript = it) },
                    onSend = { sent = voice.value.transcript }, automatic = true)
                }
            }
        }
        compose.onNodeWithTag("voice-record").assertIsNotEnabled()
        compose.onNodeWithText("Tap Speak, talk, then pause. Sends automatically.").assertExists()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithTag("voice-record").performClick()
        compose.onNodeWithText("Stop and transcribe").assertExists()
        compose.runOnIdle { connected.value = false }
        compose.onNodeWithTag("voice-record").assertIsEnabled()
        compose.onNodeWithTag("voice-record").performClick()
        compose.runOnIdle { assertEquals(1, stops) }
        compose.onNodeWithTag("voice-record").assertIsNotEnabled()
        compose.onNodeWithText("Cancel voice input").performClick()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithTag("voice-record").assertIsEnabled()
        compose.runOnIdle { voice.value = VoiceInputState(transcript = "Open Botany") }
        compose.onNodeWithTag("voice-transcript").performTextReplacement("Open Zoology")
        compose.onNodeWithTag("voice-send").performClick()
        compose.runOnIdle { assertEquals("Open Zoology", sent); connected.value = false }
        compose.onNodeWithTag("voice-send").assertIsNotEnabled()
    }
}
