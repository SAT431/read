package dev.folio.model.port

/** Selected original PDF page and deterministic text; temporary paths never enter AI context. */
data class PdfPageEvidence(val pdfPath: String, val text: String, val width: Float, val height: Float)
interface LocalPageExtraction {
    suspend fun extract(request: EngineeringRequest): PdfPageEvidence
    suspend fun crop(documentId: String, pageIndex: Int, bounds: dev.folio.model.source.SourceBounds): String
}
