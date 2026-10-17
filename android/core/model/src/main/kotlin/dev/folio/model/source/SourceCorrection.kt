package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable enum class CorrectionStatus { PROPOSED, APPROVED, SUPERSEDED, REMOVED }
@Serializable enum class CorrectionAuthor { USER, AI }
@Serializable
data class SourceCorrection(val id: String, val source: SourceAnchor, val documentChecksum: String,
    val originalText: String, val replacement: String, val expectedApprovedId: String? = null,
    val author: CorrectionAuthor, val status: CorrectionStatus = CorrectionStatus.PROPOSED)
