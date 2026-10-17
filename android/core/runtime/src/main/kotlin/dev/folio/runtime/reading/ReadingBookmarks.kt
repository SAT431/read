package dev.folio.runtime.reading

import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*

object ReadingBookmarks {
    fun fingerprint(text: String): String = ReadingTargetResolver.fingerprint(text)

    /** Structural validation deliberately permits retained bookmarks whose document is unavailable. */
    fun validate(bookmarks: Map<String, ReadingBookmark>) {
        require(bookmarks.size <= 200)
        bookmarks.forEach { (id, item) ->
            require(id == item.id && id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
            require(item.source.documentId.isNotBlank() && item.source.pageIndex >= 0 && !item.source.elementId.isNullOrBlank())
            require(item.sentenceId?.let { it.length in 1..200 } != false)
            require(item.scrollOffset in 0..1_000_000 && item.documentChecksum.length in 1..128)
            require(item.textFingerprint.matches(Regex("[a-f0-9]{64}")) && item.excerpt.length <= 160)
            require(item.createdAtEpochMillis >= 0 && item.createdAtRevision > 0)
        }
    }

    fun resolve(bookmark: ReadingBookmark, catalog: SourceCatalog): Int = ReadingTargetResolver.resolve(
        bookmark.source, bookmark.sentenceId, bookmark.documentChecksum, bookmark.textFingerprint, catalog)
}
