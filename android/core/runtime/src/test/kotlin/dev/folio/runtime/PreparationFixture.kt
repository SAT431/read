package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.engineering.*
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class PreparationFixture {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val checksum = "a".repeat(64)
    val stored = ConcurrentHashMap<PageCacheKey, SourcePage>()
    val pages = object : PageRepository {
        override suspend fun find(key: PageCacheKey) = stored[key]
        override suspend fun save(key: PageCacheKey, page: SourcePage) { stored[key] = page }
    }
    val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "cell", SourceDocument("doc", "Labelled preparation fixture.pdf", 30, checksum, "/private/fixture.pdf"), 1)))
        override suspend fun load() {}
    }
    val catalog = object : SourceCatalog {
        override fun documents() = library.entries.value.map { it.document }
        override fun chapters() = listOf(Chapter("chapter", "Labelled fixture", "doc", 0, 29))
        override fun page(documentId: String, pageIndex: Int) = stored.entries.filter { it.key.documentId == documentId && it.key.pageIndex == pageIndex }.maxByOrNull { it.key.engineeringVersion }?.value
        override fun contains(anchor: SourceAnchor) = anchor.documentId == "doc" && anchor.pageIndex in 0..29 &&
            (anchor.elementId == null || page(anchor.documentId, anchor.pageIndex)?.elements?.any { it.id == anchor.elementId } == true)
    }
    val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "fixture"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 1, 0))
    }
    val release = CompletableDeferred<Unit>(); val started = CompletableDeferred<EngineeringRequest>()
    val engineCalls = AtomicInteger(); val active = AtomicInteger(); val peak = AtomicInteger(); var failure: Exception? = null
    val jobs = PagePreparationJobs(EngineeringCoordinator(object : PageEngineer {
        override suspend fun engineer(request: EngineeringRequest): SourcePage {
            engineCalls.incrementAndGet(); val running = active.incrementAndGet(); peak.updateAndGet { maxOf(it, running) }
            try { started.complete(request); release.await(); failure?.let { throw it }; return page(request) } finally { active.decrementAndGet() }
        }
    }, pages), scope)
    fun request(id: String = "fixture", page: Int = 0) = EngineeringRequest(id, "doc", page, checksum, PageEngineeringVersion.CURRENT)
    fun page(request: EngineeringRequest) = SourcePage(request.documentId, request.pageIndex, "Labelled prepared page", listOf(
        SourceElement("prepared-p", SourceElementKind.PARAGRAPH, "Prepared native source marker.", 0)), engineeringVersion = request.engineeringVersion)
    private val registry = BuiltInComponents.registry()
    private val executor = CommandExecutor(registry, catalog)
    val original = Workspace(activities = mapOf("reader" to Activity("reader", SourceAnchor("doc", 0), ActivityData.Reader(
        annotations = mapOf("retained" to SourceAnnotation("retained", SourceAnchor("doc", 0, "old-p"), kind = AnnotationKind.NOTE, text = "Retained original note")),
        annotationDrafts = mapOf("private" to AnnotationDraft(SourceAnchor("doc", 0, "old-p"), text = "Private preparation draft"))))),
        components = mapOf("reader-view" to ComponentInstance("reader-view", "reader", ComponentKind.READER)), context = StudyContext(source = SourceAnchor("doc", 0), focusedActivityId = "reader"),
        navigation = AppNavigation(AppDestination.PDF, chapterId = "cell", pdfId = "pdf"))
    val workspace = WorkspaceSession(original, executor, object : WorkspaceRepository {
        override suspend fun load(): Workspace? = null; override suspend fun save(workspace: Workspace) {}
    }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
    val mentorCalls = AtomicInteger()
    fun session(timeout: Long = 90_000) = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
        provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
            val number = mentorCalls.incrementAndGet()
            val commands = if (number == 1) listOf(AppCommand.EngineerPage("doc", 0)) else {
                org.junit.Assert.assertTrue(request.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean)
                org.junit.Assert.assertEquals("Prepared native source marker.", request.sourceContext.getValue("page").jsonObject.getValue("elements").jsonArray.single().jsonObject.getValue("originalText").jsonPrimitive.content)
                listOf(AppCommand.WorkspaceAction(WorkspaceCommand.CreateActivity("explanation", "explanation-view", ComponentKind.EXPLANATION,
                    SourceAnchor("doc", 0, "prepared-p"), ActivityData.Explanation("Labelled explanation of the prepared marker"))), AppCommand.Navigate(AppDestination.WORKSPACE))
            }
            return MentorResponse(requestId = request.requestId, message = "Labelled prepared-source step", batch = AppCommandBatch(requestId = request.requestId,
                expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long, commands = commands), continueAfterExecution = number == 1)
        } } }, effects = { effects -> effects.forEach { jobs.start((it as AppEffect.EngineerPage).request) } }, engineeringAvailable = true,
        effectAwaiter = PagePreparationAwaiter(jobs, catalog), turnTimeoutMillis = timeout)
    suspend fun close() { scope.coroutineContext.job.cancelAndJoin() }
}
