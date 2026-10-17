package dev.folio.runtime.engineering

import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*

/** Immutable machine pages stay in their repository; approvals are overlaid only when identity still matches. */
class CorrectedSourceCatalog(private val original: SourceCatalog,
    private val corrections: () -> Map<String, SourceCorrection>) : SourceCatalog by original {
    private var lastRecords: Map<String, SourceCorrection>? = null
    private var lastBase = -1L
    private var version = 0L
    override val revision: Long get() = synchronized(this) {
        val records = corrections(); val base = original.revision
        if (records != lastRecords || base != lastBase) { lastRecords = records; lastBase = base; version++ }
        version
    }
    override fun cachedPages() = original.cachedPages().mapNotNull { page(it.documentId, it.pageIndex) }
    override fun page(documentId: String, pageIndex: Int): SourcePage? {
        val page = original.page(documentId, pageIndex) ?: return null
        if (page.documentId != documentId || page.pageIndex != pageIndex) return null
        val checksum = original.documents().firstOrNull { it.id == documentId }?.checksum
        val approved = corrections().values.filter { record -> record.status == CorrectionStatus.APPROVED &&
            record.source.documentId == documentId && record.source.pageIndex == pageIndex && record.documentChecksum == checksum &&
            page.elements.any { it.id == record.source.elementId && it.originalText == record.originalText && it.kind != SourceElementKind.FIGURE }
        }.associate { requireNotNull(it.source.elementId) to it.replacement }
        return page.copy(corrections = page.corrections + approved)
    }
}
