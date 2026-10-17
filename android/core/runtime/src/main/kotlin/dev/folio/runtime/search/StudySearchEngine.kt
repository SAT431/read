package dev.folio.runtime.search

import dev.folio.model.activity.ActivityData
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.source.AnnotationKind
import dev.folio.model.workspace.Workspace
import java.text.Normalizer
import java.util.Locale

/** Searches owned metadata and cached text; hidden answers/private drafts are never search corpus. */
class StudySearchEngine(private val curriculum: CurriculumCatalog, private val catalog: SourceCatalog) {
    val materializer = SearchMaterializer(curriculum, catalog)
    fun search(state: Workspace, pdfs: List<LibraryPdf>, checkCancelled: () -> Unit = {}): StudySearchSnapshot {
        val nav = state.navigation; val query = normalize(nav.globalQuery)
        val sourceRevision = catalog.revision
        if (query.isBlank()) return StudySearchSnapshot(state.revision, sourceRevision, nav.globalQuery, nav.searchKind, nav.searchPage)
        val words = query.split(Regex("\\s+"))
        val matches = targets(state, pdfs).distinct().mapNotNull { target ->
            checkCancelled()
            if (nav.searchKind != null && target.kind != nav.searchKind) null else {
                val candidate = try { materializer.one(target, state, pdfs) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
                candidate?.takeIf { item -> normalize(item.searchable).let { normalized -> words.all { it in normalized } } }
            }
        }.sortedWith(compareBy<SearchCandidate> { if (normalize(it.result.title) == query) 0 else if (query in normalize(it.result.title)) 1 else 2 }
            .thenBy { it.result.target.kind.ordinal }.thenBy { it.result.target.ownerId }.thenBy { it.result.target.pageIndex ?: -1 }
            .thenBy { it.result.target.attemptNumber ?: -1 }.thenBy { it.result.target.recordId.orEmpty() }).toList()
        val start = (nav.searchPage.toLong() * 40L).coerceAtMost(matches.size.toLong()).toInt()
        return StudySearchSnapshot(state.revision, sourceRevision, nav.globalQuery, nav.searchKind, nav.searchPage, matches.size,
            matches.drop(start).take(40).map { it.result })
    }
    private fun targets(state: Workspace, pdfs: List<LibraryPdf>) = sequence {
        curriculum.chapters().forEach { yield(SearchTarget(SearchKind.CHAPTER, it.id)) }
        pdfs.forEach { yield(SearchTarget(SearchKind.PDF, it.id)) }
        catalog.cachedPages().filterNot { it.isFixture }.forEach { page -> page.elements.forEach { yield(SearchTarget(SearchKind.PASSAGE, page.documentId, it.id, page.pageIndex)) } }
        state.activities.toSortedMap().forEach { (id, activity) -> when (val data = activity.data) {
            is ActivityData.Reader -> {
                data.annotations.values.filter { it.kind == AnnotationKind.NOTE }.forEach { yield(SearchTarget(SearchKind.NOTE, id, it.id)) }
                data.bookmarks.values.forEach { yield(SearchTarget(SearchKind.BOOKMARK, id, it.id)) }
            }
            is ActivityData.Quiz -> {
                data.questions.forEach { yield(SearchTarget(SearchKind.QUIZ, id, it.id, attemptNumber = data.attemptNumber)) }
                data.pastAttempts.forEach { attempt -> attempt.questions.forEach { yield(SearchTarget(SearchKind.QUIZ, id, it.id, attemptNumber = attempt.number)) } }
            }
            is ActivityData.Recall -> yield(SearchTarget(SearchKind.RECALL, id))
            else -> Unit
        } }
    }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
}
