package dev.folio.model.library

import dev.folio.model.source.SourceDocument
import kotlinx.serialization.Serializable

@Serializable
data class LibraryPdf(val id: String, val chapterId: String, val document: SourceDocument, val importedAt: Long)
