package dev.folio.model.source

import kotlinx.serialization.Serializable

/** Private unfinished editor state, never a proposal or an approved source overlay. */
@Serializable
data class SourceCorrectionDraft(val source: SourceAnchor, val documentChecksum: String,
    val originalText: String, val replacement: String, val expectedApprovedId: String? = null)
