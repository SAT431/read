package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable
enum class SourceElementKind { HEADING, PARAGRAPH, FIGURE, CAPTION, TABLE, LIST }

/** Transcription and original assets are separate from approved correction revisions. */
@Serializable
data class SourceElement(
    val id: String,
    val kind: SourceElementKind,
    val originalText: String,
    val order: Int,
    val bounds: SourceBounds? = null,
    val parentId: String? = null,
    val mediaId: String? = null,
    val relatedElementIds: List<String> = emptyList(),
    val mediaBounds: SourceBounds? = null,
)
