package dev.folio.runtime.reading

import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*

object ReadingConfirmations {
    val kinds = setOf(SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST)
    fun id(source: SourceAnchor, sentenceId: String?): String = ReadingTargetResolver.fingerprint(
        "${source.documentId.length}:${source.documentId}:${source.pageIndex}:${source.elementId?.length}:${source.elementId}:${sentenceId?.length}:$sentenceId")
    fun validate(reader: ActivityData.Reader) {
        require(reader.readingRevision in 0..1_000_000 && reader.readingConfirmations.size <= 1000)
        reader.readingConfirmations.forEach { (key, record) ->
            require(key == record.id && key == id(record.source, record.sentenceId))
            require(record.source.documentId.isNotBlank() && record.source.pageIndex >= 0 && !record.source.elementId.isNullOrBlank())
            require(record.sentenceId?.let { it.length in 1..200 } != false)
            require(record.documentChecksum.length in 1..128 && record.textFingerprint.matches(Regex("[a-f0-9]{64}")))
            require(record.excerpt.length <= 160 && record.reportedAtEpochMillis >= 0 && record.version in 1..reader.readingRevision)
        }
    }
    fun isCurrent(record: ReadingConfirmation, catalog: SourceCatalog): Boolean = runCatching {
        val index = ReadingTargetResolver.resolve(record.source, record.sentenceId, record.documentChecksum, record.textFingerprint, catalog)
        require(catalog.page(record.source.documentId, record.source.pageIndex)?.elements?.sortedBy { it.order }?.get(index)?.kind in kinds)
    }.isSuccess

    /** Counts only explicit reports against current wording. Scrolling, focus and time do not enter this projection. */
    fun progress(reader: ActivityData.Reader, page: SourcePage, catalog: SourceCatalog): ReadingProgress {
        val onPage = reader.readingConfirmations.values.filter { it.source.documentId == page.documentId && it.source.pageIndex == page.pageIndex }
        val passages = page.elements.filter { it.kind in kinds && (page.corrections[it.id] ?: it.originalText).isNotBlank() }
        val sentences = SentenceIndex().compile(page).groupBy { it.source.elementId }
        val sentenceIds = sentences.mapValues { (_, values) -> values.map { it.id }.toSet() }
        val fingerprints = passages.associate { it.id to ReadingTargetResolver.fingerprint(page.corrections[it.id] ?: it.originalText) }
        val checksum = catalog.documents().firstOrNull { it.id == page.documentId }?.checksum
        val current = onPage.filter { record -> checksum != null && record.documentChecksum == checksum &&
            fingerprints[record.source.elementId] == record.textFingerprint &&
            (record.sentenceId == null || sentenceIds[record.source.elementId]?.contains(record.sentenceId) == true) }
        val byElement = current.groupBy { it.source.elementId }
        val confirmed = passages.count { element ->
            val reports = byElement[element.id].orEmpty()
            reports.any { it.sentenceId == null } || sentences[element.id]?.takeIf { it.isNotEmpty() }?.all { sentence ->
                reports.any { it.sentenceId == sentence.id }
            } == true
        }
        return ReadingProgress(passages.size, confirmed, current.count { it.sentenceId != null },
            onPage.size - current.size, current.maxByOrNull { it.version }?.id)
    }
}
