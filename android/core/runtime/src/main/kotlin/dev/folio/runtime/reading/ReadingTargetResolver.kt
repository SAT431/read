package dev.folio.runtime.reading

import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import java.security.MessageDigest

/** Shared immutable-source checks for bookmarks and explicit reading reports. */
object ReadingTargetResolver {
    fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun resolve(source: SourceAnchor, sentenceId: String?, checksum: String, expectedFingerprint: String, catalog: SourceCatalog): Int {
        require(catalog.documents().any { it.id == source.documentId && it.checksum == checksum }) { "Saved document is unavailable or changed" }
        val page = catalog.page(source.documentId, source.pageIndex) ?: error("Saved page is unavailable")
        val elements = page.elements.sortedBy { it.order }
        val index = elements.indexOfFirst { it.id == source.elementId }
        require(index >= 0) { "Saved passage is unavailable" }
        val element = elements[index]
        require(fingerprint(page.corrections[element.id] ?: element.originalText) == expectedFingerprint) { "Saved wording changed; the original target is retained" }
        sentenceId?.let { id -> require(SentenceIndex().compileElement(page, element.id).any { it.id == id && it.source == source }) { "Saved sentence is unavailable" } }
        return index
    }
}
