package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

/** Explicit pre-check report, independent of the selected option and the answer key. */
@Serializable
data class AnswerConfidence(val level: ConfidenceLevel, val selectedOption: Int, val recordedAtEpochMillis: Long, val byAssistant: Boolean = false)
