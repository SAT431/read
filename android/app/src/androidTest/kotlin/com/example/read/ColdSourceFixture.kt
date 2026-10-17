package com.example.read

import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*

/** Only temporary Room databases and labelled original sample text; never accesses the learner database. */
internal class ColdSourceFixture(database: FolioDatabase, backup: dev.folio.local.source.SourceCacheBackup? = null) {
    val base: SourceCatalog = ArchitectureSources.catalog()
    val checksum = "a".repeat(64)
    val warm: SourceCatalog = object : SourceCatalog by base { override fun documents() = base.documents().map { it.copy(checksum = checksum) } }
    val pages = RoomPageRepository(database.pages(), backup)
    val cacheRepair = dev.folio.runtime.source.SourceCacheRepairSession(pages)
    val catalog = object : SourceCatalog {
        override val revision get() = pages.revision.value
        override fun cachedPages() = pages.cachedPages().filter { it.first.documentChecksum == checksum }.map { it.second }
        override fun documents() = warm.documents()
        override fun chapters() = warm.chapters()
        override fun page(documentId: String, pageIndex: Int) = pages.page(documentId, pageIndex, checksum)
        override fun contains(anchor: SourceAnchor): Boolean {
            val document = documents().firstOrNull { it.id == anchor.documentId } ?: return false
            return anchor.pageIndex in 0 until document.pageCount && (anchor.elementId == null ||
                page(anchor.documentId, anchor.pageIndex)?.elements?.any { it.id == anchor.elementId } == true)
        }
    }
    val curriculum = Neet2026Catalog()
    val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>())
        override suspend fun load() {}
    }
    private val registry = BuiltInComponents.registry()
    private val executor = CommandExecutor(registry, catalog)
    val repository = RoomWorkspaceRepository(database.workspace(), object : WorkspaceRepository {
        override suspend fun load(): Workspace? = null
        override suspend fun save(workspace: Workspace) {}
    })
    val session = WorkspaceSession(Workspace(), executor, repository, WorkspaceValidator(registry, catalog),
        reducer = LearningReducer(catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog, cacheRepair))

    fun sampleState(): Workspace {
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p1")
        val other = source.copy(elementId = "cell-p2")
        val questions = listOf(Question("cached-q1", "Labelled cached question one", listOf("A", "B", "C", "D"), 0, "Labelled saved key", listOf(source)),
            Question("cached-q2", "Labelled cached question two", listOf("A", "B", "C", "D"), 2, "Labelled saved key", listOf(other)))
        val created = CommandExecutor(registry, warm).execute(ArchitectureActivities.initial(), CommandBatch(requestId = "seed", commands = listOf(
            WorkspaceCommand.CreateActivity("cached-quiz", "cached-quiz-view", ComponentKind.QUIZ, source, ActivityData.Quiz(questions)))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.error, created.trace.accepted)
        val learning = LearningReducer(warm) { 100L }
        var state = created.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE))
        for (event in listOf(LearningInteraction.SelectAnswer("cached-quiz", "cached-q1", 1),
            LearningInteraction.SetAnswerConfidence("cached-quiz", "cached-q1", 1, null, ConfidenceLevel.HIGH),
            LearningInteraction.SubmitAnswer("cached-quiz", "cached-q1"),
            LearningInteraction.CreateAnnotation("reader", "saved-note", source, null, AnnotationKind.NOTE, "Labelled retained note"),
            LearningInteraction.SaveAnnotationDraft("reader", "saved-draft", AnnotationDraft(source, text = "Labelled unfinished private draft")),
            LearningInteraction.AddReadingBookmark("reader", "saved-bookmark", source),
            LearningInteraction.ConfirmReading("reader", source, expectedReadingRevision = 0))) state = learning.reduce(state, event)
        return state
    }
    suspend fun act(command: AppCommand) {
        val result = session.dispatchApp(AppCommandBatch(requestId = "recovered-touch", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
        assertTrue(result.trace.error, result.trace.accepted)
    }
}
