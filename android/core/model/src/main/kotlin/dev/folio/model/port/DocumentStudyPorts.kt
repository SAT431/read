package dev.folio.model.port

import dev.folio.model.study.*
import dev.folio.model.source.SourceDocument
import kotlinx.coroutines.flow.StateFlow

interface DocumentOutlineRepository {
    val outlines: StateFlow<Map<String, DocumentOutline>>
    fun get(documentId: String): DocumentOutline? = outlines.value[documentId]
    suspend fun load(document: SourceDocument)
    suspend fun save(outline: DocumentOutline)
}
data class DocumentChunk(val path: String, val firstPage: Int, val pageCount: Int)
interface DocumentChunkSource {
    suspend fun create(document: SourceDocument, firstPage: Int, count: Int): DocumentChunk
    suspend fun release(chunk: DocumentChunk)
}
interface DocumentMapper { suspend fun map(chunk: DocumentChunk): List<DocumentTopic> }
