package dev.folio.model.source

import kotlinx.serialization.Serializable

/** Immutable native source target. createdAtRevision is the owner's bookmark edit sequence, not app revision. */
@Serializable
data class ReadingBookmark(val id: String, val source: SourceAnchor, val sentenceId: String? = null,
    val scrollOffset: Int = 0, val documentChecksum: String, val textFingerprint: String,
    val excerpt: String, val createdAtEpochMillis: Long, val createdAtRevision: Long,
    val byAssistant: Boolean = false)
