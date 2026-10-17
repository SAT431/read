package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.search.StudySearchEngine
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

internal class AssistantContinuationFixture {
    val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "fixture"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 1, 0))
    }
    val pdfEntries = MutableStateFlow(emptyList<LibraryPdf>())
    val library = object : PdfLibrary {
        override val entries = pdfEntries; override suspend fun load() {}
    }
    class History : AssistantHistoryRepository {
        var saved: AssistantHistory? = null; var failAcknowledgement = false
        override suspend fun load() = saved
        override suspend fun save(history: AssistantHistory) {
            if (failAcknowledgement && history.state.acceptedActions.isNotEmpty()) error("fixture storage failure")
            saved = history
        }
    }
    val history = History()
    private val initial = TestFixture.reader().let { state -> state.copy(navigation = AppNavigation(AppDestination.SEARCH),
        activities = state.activities + ("read" to state.activities.getValue("read").copy(data = ActivityData.Reader(
            annotations = mapOf("note" to SourceAnnotation("note", TestFixture.source, kind = AnnotationKind.NOTE, text = "Cell note")),
            annotationDrafts = mapOf("private" to AnnotationDraft(TestFixture.source, text = "Private continuation draft")))))) }
    val workspace = WorkspaceSession(initial, TestFixture.executor, object : WorkspaceRepository {
        override suspend fun load(): Workspace? = null; override suspend fun save(workspace: Workspace) {}
    }, WorkspaceValidator(TestFixture.registry, TestFixture.catalog), appExecutor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog))
    var locked = false
    var snapshot: StudySearchSnapshot? = null
    val effects = mutableListOf<AppEffect>()
    fun session(reply: suspend (MentorRequest) -> MentorResponse, timeout: Long = 90_000,
        ready: suspend (Workspace) -> Unit = { snapshot = StudySearchEngine(curriculum, TestFixture.catalog).search(it, library.entries.value) }) =
        AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = TestFixture.catalog, searchSnapshot = { state -> snapshot?.takeIf { it.workspaceRevision == state.revision } }),
            library, TestFixture.catalog, provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest) = reply(request) } },
            effects = { effects.addAll(it) }, interactionLocked = { locked }, history = history, readyForContinuation = ready, turnTimeoutMillis = timeout)
    fun response(request: MentorRequest, command: AppCommand, more: Boolean = false) = MentorResponse(requestId = request.requestId,
        message = "Labelled step accepted", batch = AppCommandBatch(requestId = request.requestId,
            expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long, commands = listOf(command)), continueAfterExecution = more)
    fun search(request: MentorRequest) = response(request, AppCommand.SearchStudy("Cell", SearchKind.NOTE), true)
    fun openResult(request: MentorRequest): MentorResponse {
        val result = Json.decodeFromJsonElement<SearchResult>(request.capabilityContext.getValue("search").jsonObject.getValue("results").jsonArray.single())
        return response(request, AppCommand.OpenSearchResult(result.target, result.fingerprint))
    }
}
