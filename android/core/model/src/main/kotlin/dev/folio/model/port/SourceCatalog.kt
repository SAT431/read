package dev.folio.model.port

import dev.folio.model.source.*

interface SourceCatalog {
    val revision: Long get() = 0
    fun fidelity(documentId: String, pageIndex: Int): kotlinx.serialization.json.JsonObject? = null
    /** Already cached readable pages only; never extracts or fetches a PDF. */
    fun cachedPages(): List<SourcePage> = emptyList()
    fun documents(): List<SourceDocument>
    fun chapters(): List<Chapter>
    fun page(documentId: String, pageIndex: Int): SourcePage?
    fun contains(anchor: SourceAnchor): Boolean
}
