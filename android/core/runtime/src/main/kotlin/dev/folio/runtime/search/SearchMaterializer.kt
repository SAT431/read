package dev.folio.runtime.search

import dev.folio.model.activity.ActivityData
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.reading.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest

internal data class SearchCandidate(val result: SearchResult, val searchable: String)
/** Every result and open is reconstructed from owned native data, never a model-provided title or URL. */
class SearchMaterializer(private val curriculum: CurriculumCatalog, private val catalog: SourceCatalog) {
    private val json = Json { encodeDefaults = true }
    fun fingerprint(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    internal fun one(target: SearchTarget, state: Workspace, pdfs: List<LibraryPdf>): SearchCandidate {
        var source: SourceAnchor? = null; var fixture = false; var available = true
        val title: String; val text: String; var detail: String; val evidence: String
        val canonical: SearchTarget
        when (target.kind) {
            SearchKind.CHAPTER -> {
                val chapter = curriculum.chapters().first { it.id == target.ownerId }
                title = chapter.title; text = "${chapter.subject} ${chapter.title}"; detail = chapter.subject.name.lowercase(); evidence = json.encodeToString(listOf(chapter.id, chapter.subject.name, chapter.title, chapter.syllabusUnit.toString(), chapter.order.toString(), chapter.sharedBiology.toString()))
                canonical = SearchTarget(SearchKind.CHAPTER, chapter.id)
            }
            SearchKind.PDF -> {
                val pdf = pdfs.first { it.id == target.ownerId }
                title = pdf.document.title; text = title; detail = "${pdf.document.pageCount} file pages"; evidence = json.encodeToString(pdf)
                canonical = SearchTarget(SearchKind.PDF, pdf.id)
            }
            SearchKind.PASSAGE -> {
                val page = catalog.page(target.ownerId, requireNotNull(target.pageIndex)) ?: error("Cached page unavailable")
                require(!page.isFixture && page.documentId == target.ownerId && page.pageIndex == target.pageIndex)
                val element = page.elements.first { it.id == target.recordId && it.kind in setOf(SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST) }
                source = SourceAnchor(page.documentId, page.pageIndex, element.id)
                require(dev.folio.runtime.source.SavedSourceReferences.available(source, catalog))
                title = catalog.documents().first { it.id == page.documentId }.title
                text = page.corrections[element.id] ?: element.originalText; detail = "Cached passage · file page ${page.pageIndex + 1}"
                evidence = "${catalog.documents().first { it.id == page.documentId }.checksum}:${json.encodeToString(element)}:$text"
                fixture = page.isFixture; canonical = SearchTarget(SearchKind.PASSAGE, page.documentId, element.id, page.pageIndex)
            }
            SearchKind.NOTE -> {
                val reader = state.activities.getValue(target.ownerId).data as ActivityData.Reader
                val note = reader.annotations.getValue(requireNotNull(target.recordId)); require(note.kind == AnnotationKind.NOTE)
                title = "Saved note"; text = note.text; source = note.source
                detail = "${note.author.name.lowercase()} note · file page ${source.pageIndex + 1}"
                if (runCatching { SavedNotes.resolve(note.source, note.sentenceId, catalog) }.isFailure) detail += " · source unavailable"
                evidence = SavedNotes.fingerprint(target.ownerId, note); canonical = SearchTarget(SearchKind.NOTE, target.ownerId, note.id)
            }
            SearchKind.BOOKMARK -> {
                val reader = state.activities.getValue(target.ownerId).data as ActivityData.Reader
                val bookmark = reader.bookmarks.getValue(requireNotNull(target.recordId))
                title = "Reading bookmark"; text = bookmark.excerpt; source = bookmark.source; detail = "File page ${source.pageIndex + 1} · saved position"
                available = runCatching { ReadingBookmarks.resolve(bookmark, catalog) }.isSuccess
                evidence = json.encodeToString(bookmark); canonical = SearchTarget(SearchKind.BOOKMARK, target.ownerId, bookmark.id)
            }
            SearchKind.QUIZ -> {
                val quiz = state.activities.getValue(target.ownerId).data as ActivityData.Quiz
                val questions = if (target.attemptNumber == quiz.attemptNumber) quiz.questions else quiz.pastAttempts.first { it.number == target.attemptNumber }.questions
                val question = questions.first { it.id == target.recordId }
                title = question.prompt; text = question.prompt; source = question.sources.first(); detail = "Quiz attempt ${target.attemptNumber}"
                evidence = json.encodeToString(question); canonical = SearchTarget(SearchKind.QUIZ, target.ownerId, question.id, attemptNumber = target.attemptNumber)
            }
            SearchKind.RECALL -> {
                val activity = state.activities.getValue(target.ownerId); val recall = activity.data as ActivityData.Recall
                title = recall.prompt; text = recall.prompt; source = recall.sources.first(); detail = "Saved recall prompt"
                evidence = recall.prompt + json.encodeToString(recall.sources); canonical = SearchTarget(SearchKind.RECALL, activity.id)
            }
        }
        require(canonical == target) { "Invalid search target shape" }
        val sourceTitle = source?.let { anchor -> catalog.documents().firstOrNull { it.id == anchor.documentId }?.title }.orEmpty()
        if (source != null && target.kind != SearchKind.PASSAGE && sourceTitle.isNotEmpty()) detail += " · $sourceTitle"
        if (source != null) fixture = catalog.page(source.documentId, source.pageIndex)?.isFixture == true
        val hash = fingerprint(json.encodeToString(target) + ":" + evidence)
        return SearchCandidate(SearchResult(target, title.take(240), text.take(240), detail, hash, available, source, fixture), "$title $text $detail $sourceTitle")
    }
}
