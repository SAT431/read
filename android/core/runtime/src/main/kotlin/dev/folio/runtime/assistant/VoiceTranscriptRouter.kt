package dev.folio.runtime.assistant

import dev.folio.model.assistant.VoiceInputState

/** Submission accepts the same utterance path as typing; failed handoffs retain the draft. */
class VoiceTranscriptRouter(private val submit: (String) -> Boolean) {
    fun received(text: String, automaticAtStart: Boolean, automaticNow: Boolean): VoiceInputState {
        val draft = VoiceInputState(transcript = text,
            error = if (text.isBlank()) "No speech was recognized. Try again." else null)
        return if (automaticAtStart && automaticNow) send(draft) else draft
    }
    fun send(state: VoiceInputState): VoiceInputState {
        if (state.recording || state.processing || state.transcript.isBlank()) return state
        return if (submit(state.transcript)) VoiceInputState()
            else state.copy(error = "Transcript kept. Wait for the assistant or reconnect, then send again.")
    }
}
