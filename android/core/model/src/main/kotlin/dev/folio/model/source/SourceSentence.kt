package dev.folio.model.source

import kotlinx.serialization.Serializable

/** UTF-16 ranges in the displayed passage; its original source element stays intact. */
@Serializable
data class SourceSentence(val id: String, val source: SourceAnchor, val start: Int, val end: Int,
    val text: String, val usesCorrection: Boolean = false)
