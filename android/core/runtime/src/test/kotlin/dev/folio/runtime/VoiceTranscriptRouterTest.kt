package dev.folio.runtime

import dev.folio.model.assistant.VoiceInputState
import dev.folio.runtime.assistant.VoiceTranscriptRouter
import org.junit.Assert.*
import org.junit.Test

class VoiceTranscriptRouterTest {
    @Test fun automaticSpeechUsesUnchangedUtteranceAndClearsOnlyAcceptedSubmission() {
        val received = mutableListOf<String>()
        val router = VoiceTranscriptRouter { received.add(it); true }
        val result = router.received("Agla page kholo", true, true)
        assertEquals(listOf("Agla page kholo"), received)
        assertEquals(VoiceInputState(), result)
    }
    @Test fun reviewModeOrChangingModeDuringRecordingNeverAutoSubmits() {
        val router = VoiceTranscriptRouter { fail("Unexpected automatic submission"); true }
        for ((atStart, now) in listOf(false to false, false to true, true to false)) {
            val result = router.received("Open page five", atStart, now)
            assertEquals("Open page five", result.transcript)
            assertNull(result.error)
        }
    }
    @Test fun busyAssistantKeepsTranscriptAndManualRetryUsesEditedWords() {
        val received = mutableListOf<String>()
        var ready = false
        val router = VoiceTranscriptRouter { received.add(it); ready }
        val draft = router.received("Open Botany", true, true)
        assertEquals("Open Botany", draft.transcript)
        assertNotNull(draft.error)
        ready = true
        assertEquals(VoiceInputState(), router.send(draft.copy(transcript = "Open Zoology")))
        assertEquals(listOf("Open Botany", "Open Zoology"), received)
    }
    @Test fun silenceAndActiveCaptureCannotSendAnUtterance() {
        val router = VoiceTranscriptRouter { fail("Unexpected submission"); true }
        assertNotNull(router.received("  ", true, true).error)
        for (state in listOf(VoiceInputState(recording = true, transcript = "next"),
            VoiceInputState(processing = true, transcript = "next"))) assertEquals(state, router.send(state))
    }
}
