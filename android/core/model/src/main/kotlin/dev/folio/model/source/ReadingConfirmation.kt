package dev.folio.model.source

import kotlinx.serialization.Serializable

/** Explicit reading report, not gaze, comprehension or a mastery estimate. */
@Serializable
data class ReadingConfirmation(val id: String, val source: SourceAnchor, val sentenceId: String? = null,
    val documentChecksum: String, val textFingerprint: String, val excerpt: String,
    val reportedAtEpochMillis: Long, val version: Long, val byAssistant: Boolean = false)

@Serializable
data class ReadingProgress(val passages: Int, val confirmedPassages: Int, val sentenceCheckpoints: Int,
    val outdatedCheckpoints: Int, val lastConfirmedId: String? = null)
