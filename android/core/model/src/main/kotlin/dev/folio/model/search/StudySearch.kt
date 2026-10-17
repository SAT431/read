package dev.folio.model.search

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable enum class SearchKind { CHAPTER, PDF, PASSAGE, BOOKMARK, NOTE, QUIZ, RECALL }
@Serializable data class SearchTarget(val kind: SearchKind, val ownerId: String, val recordId: String? = null,
    val pageIndex: Int? = null, val attemptNumber: Int? = null)
@Serializable data class SearchResult(val target: SearchTarget, val title: String, val excerpt: String,
    val detail: String, val fingerprint: String, val canOpen: Boolean, val source: SourceAnchor? = null, val isFixture: Boolean = false)
@Serializable data class StudySearchSnapshot(val workspaceRevision: Long = -1, val sourceRevision: Long = -1,
    val query: String = "", val kind: SearchKind? = null, val page: Int = 0, val total: Int = 0,
    val results: List<SearchResult> = emptyList(), val error: String? = null,
    @kotlinx.serialization.Transient val libraryEntries: List<dev.folio.model.library.LibraryPdf> = emptyList()) {
    val pageCount get() = (total.toLong() + 39L).div(40L).toInt()
}
