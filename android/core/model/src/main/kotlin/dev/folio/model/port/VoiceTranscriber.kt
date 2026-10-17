package dev.folio.model.port

import kotlinx.serialization.Serializable

data class RecordedAudio(val path: String)
@Serializable
data class Transcript(val schemaVersion: Int = 1, val text: String, val language: String? = null,
    val durationSeconds: Double? = null)
interface VoiceTranscriber { suspend fun transcribe(audio: RecordedAudio): Transcript }
