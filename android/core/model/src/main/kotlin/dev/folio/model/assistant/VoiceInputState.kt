package dev.folio.model.assistant

import kotlinx.serialization.Serializable

@Serializable
data class VoiceInputState(val recording: Boolean = false, val processing: Boolean = false,
    val transcript: String = "", val error: String? = null)
