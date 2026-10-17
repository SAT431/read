package dev.folio.model.port

import dev.folio.model.source.SourcePage

/** Cache identity includes immutable PDF checksum and compiler version. */
data class PageCacheKey(val documentChecksum: String, val documentId: String, val pageIndex: Int, val engineeringVersion: Int)
interface PageRepository {
    suspend fun find(key: PageCacheKey): SourcePage?
    suspend fun save(key: PageCacheKey, page: SourcePage)
}
