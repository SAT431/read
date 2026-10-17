package dev.folio.runtime.search

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.LibraryNavigationReducer
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.*
import dev.folio.runtime.reading.*

class SearchResultNavigation(private val executor: CommandExecutor, private val curriculum: CurriculumCatalog, private val catalog: SourceCatalog) {
    private val materializer = SearchMaterializer(curriculum, catalog)
    fun open(state: Workspace, target: SearchTarget, expectedFingerprint: String, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Workspace {
        val current = try { materializer.one(target, state, pdfs).result }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error("Search target unavailable; search again") }
        require(current.fingerprint == expectedFingerprint) { "Search result changed; search again" }
        require(current.canOpen) { "Saved source is unavailable or changed" }
        val result = when (target.kind) {
            SearchKind.CHAPTER -> LibraryNavigationReducer(curriculum).reduce(state, AppCommand.OpenChapterLibrary(target.ownerId), pdfs)!!
            SearchKind.PDF -> LibraryNavigationReducer(curriculum).reduce(state, AppCommand.OpenPdf(target.ownerId), pdfs)!!
            SearchKind.NOTE -> {
                val note = (state.activities.getValue(target.ownerId).data as ActivityData.Reader).annotations.getValue(requireNotNull(target.recordId))
                state.copy(navigation = state.navigation.copy(destination = AppDestination.NOTES,
                    notesFilter = dev.folio.model.source.NotesFilter.NOTES, notesQuery = note.text.take(200)))
            }
            SearchKind.BOOKMARK -> {
                val bookmark = (state.activities.getValue(target.ownerId).data as ActivityData.Reader).bookmarks.getValue(requireNotNull(target.recordId))
                LearningReducer(catalog).reduce(state, LearningInteraction.OpenReadingBookmark(target.ownerId, bookmark.id, bookmark.createdAtRevision), policy.actor)
            }
            SearchKind.QUIZ, SearchKind.RECALL -> StudyTargetNavigation(executor, catalog, curriculum).open(state,
                SavedStudyTarget(target.ownerId, target.recordId, requireNotNull(current.source),
                    if (target.kind == SearchKind.QUIZ) {
                        val quiz = state.activities.getValue(target.ownerId).data as ActivityData.Quiz
                        val questions = if (target.attemptNumber == quiz.attemptNumber) quiz.questions else quiz.pastAttempts.first { it.number == target.attemptNumber }.questions
                        questions.first { it.id == target.recordId }.prompt
                    } else (state.activities.getValue(target.ownerId).data as ActivityData.Recall).prompt, target.attemptNumber), pdfs, policy)
            SearchKind.PASSAGE -> {
                val source = requireNotNull(current.source)
                val owner = state.activities.values.firstOrNull { it.data is ActivityData.Reader && it.source.documentId == source.documentId }
                val id = owner?.id ?: "search-reader-${materializer.fingerprint(source.documentId).take(24)}"
                var next = state
                if (owner == null) {
                    val created = executor.execute(state, CommandBatch(requestId = "search-reader", commands = listOf(
                        WorkspaceCommand.CreateActivity(id, "$id-view", ComponentKind.READER, source, ActivityData.Reader()))), policy)
                    require(created.trace.accepted) { created.trace.error.orEmpty() }; next = created.state
                }
                SavedReadingNavigation(catalog).open(next, id, source, null, SavedNotes.resolve(source, null, catalog), 0, policy.actor)
            }
        }
        return if (target.kind in setOf(SearchKind.CHAPTER, SearchKind.PDF, SearchKind.NOTE)) result else
            current.source?.let { StudyPdfContext.sync(result, it, pdfs, curriculum) } ?: result
    }
}
