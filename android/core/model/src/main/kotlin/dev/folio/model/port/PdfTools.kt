package dev.folio.model.port

import dev.folio.model.source.SourceBounds

/** Prebuilt extraction adapter. The model can request tools; it cannot submit code to execute. */
interface PdfTools {
    suspend fun render(documentId: String, pageIndex: Int): String
    suspend fun crop(documentId: String, pageIndex: Int, bounds: SourceBounds): String
}
