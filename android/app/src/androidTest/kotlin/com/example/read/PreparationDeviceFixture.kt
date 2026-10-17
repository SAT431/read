package com.example.read

import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import com.example.read.engineering.LocalPageEngineer
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.catalog.*
import dev.folio.local.database.FolioDatabase
import dev.folio.local.library.LocalPdfLibrary
import dev.folio.local.media.LocalMediaRepository
import dev.folio.local.pdf.*
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.local.snapshot.*
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.engineering.*
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*

/** All files/Room/history are private labelled fixtures; the live option sends only its generated PDF. */
internal class PreparationDeviceFixture(base: Context, val scope: CoroutineScope, live: Boolean = false, sourcePdf: LibraryPdf? = null) {
    val root = File(base.cacheDir, "preparation-device-${UUID.randomUUID()}").apply { mkdirs() }
    val context = object : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    }
    val originalFile = File(File(context.filesDir, "pdfs").apply { mkdirs() }, "fixture.pdf")
    val marker = "Amber circles form a labelled group."
    init {
        if(sourcePdf!=null) File(requireNotNull(sourcePdf.document.localPdfPath)).copyTo(originalFile)
        else {
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val sheet = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                val paint = Paint().apply { color = Color.BLACK; textSize = 19f; isAntiAlias = true }
                sheet.canvas.drawColor(Color.WHITE)
                sheet.canvas.drawText(if (index == 0) "Labelled preparation page" else "CONTROL_OTHER_PAGE", 40f, 70f, paint)
                if (index == 0) { sheet.canvas.drawText(marker, 40f, 120f, paint); sheet.canvas.drawText("Controlled test page, not textbook content.", 40f, 165f, paint) }
                if (index == 0) sheet.canvas.drawRect(80f, 240f, 320f, 440f, Paint().apply { color = Color.RED })
                pdf.finishPage(sheet)
            }
            originalFile.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        }
    }
    val originalBytes = originalFile.readBytes()
    val checksum = MessageDigest.getInstance("SHA-256").digest(originalBytes).joinToString("") { "%02x".format(it) }
    val database = Room.databaseBuilder(base, FolioDatabase::class.java, File(root, "fixture.db").absolutePath).build()
    val curriculum = Neet2026Catalog()
    val pdf = LibraryPdf("preparation-pdf", sourcePdf?.chapterId ?: curriculum.chapters().first().id,
        SourceDocument("preparation-document", if(sourcePdf!=null) "Cell PDF · isolated QA copy" else "Labelled preparation.pdf", sourcePdf?.document?.pageCount ?: 2, checksum, originalFile.path), 1)
    val library = LocalPdfLibrary(context.filesDir, database.library())
    val pages = RoomPageRepository(database.pages())
    val catalog = LibrarySourceCatalog(library, curriculum, InMemorySourceCatalog(emptyList(), emptyList(), emptyList()), pages)
    val tools = NativePdfTools(context.filesDir, catalog)
    val media = LocalMediaRepository(File(context.filesDir, "source-assets"))
    val outlines = dev.folio.local.study.AtomicDocumentOutlines(File(context.filesDir, "document-maps"))
    val release = CompletableDeferred<Unit>(); val started = CompletableDeferred<EngineeringRequest>(); val engineCalls = AtomicInteger()
    @Volatile var failureSummary: String? = null
    private val backend: PageEngineer = if (live) LocalPageEngineer(KeystoreProviderProfileStore(base), LocalPdfPageExtraction(context, catalog, tools),
        base.assets.open("native-page-draft.md").bufferedReader().use { it.readText() }, pages::stageFidelity) else object : PageEngineer {
        override suspend fun engineer(request: EngineeringRequest) = SourcePage(request.documentId, request.pageIndex, "Labelled prepared page",
            listOf(SourceElement("prepared-p", SourceElementKind.PARAGRAPH, marker, 0, SourceBounds(40f,100f,550f,150f)),
                SourceElement("prepared-figure", SourceElementKind.FIGURE, "Labelled original red figure", 1, SourceBounds(80f,240f,320f,440f),
                    mediaId = tools.crop(request.documentId, request.pageIndex, SourceBounds(80f,240f,320f,440f)))),
            engineeringVersion = request.engineeringVersion, widthPoints = 600f, heightPoints = 800f)
    }
    val jobs = PagePreparationJobs(EngineeringCoordinator(object : PageEngineer {
        override suspend fun engineer(request: EngineeringRequest): SourcePage {
            engineCalls.incrementAndGet(); started.complete(request); if (!live) release.await()
            try { return backend.engineer(request) }
            catch (error: Exception) {
                failureSummary = error.javaClass.simpleName + " at " + error.stackTrace.filter {
                    it.className.startsWith("dev.folio.") || it.className.startsWith("com.example.read.")
                }.take(4).joinToString { "${it.fileName}:${it.lineNumber}" }
                throw error
            }
        }
    }, pages), scope)
    val registry = BuiltInComponents.registry()
    private val executor = CommandExecutor(registry, catalog)
    val original = Workspace(activities = mapOf("reader" to Activity("reader", SourceAnchor(pdf.document.id, 0), ActivityData.Reader(
        annotations = mapOf("retained" to SourceAnnotation("retained", SourceAnchor(pdf.document.id, 0, "old-p"), kind = AnnotationKind.NOTE, text = "Retained preparation note")),
        annotationDrafts = mapOf("private" to AnnotationDraft(SourceAnchor(pdf.document.id, 0, "old-p"), text = "Private preparation draft"))))),
        components = mapOf("reader-view" to ComponentInstance("reader-view", "reader", ComponentKind.READER)),
        context = StudyContext(source = SourceAnchor(pdf.document.id, 0), focusedActivityId = "reader"),
        navigation = AppNavigation(AppDestination.PDF, chapterId = pdf.chapterId, pdfId = pdf.id))
    val repository = RoomWorkspaceRepository(database.workspace(), AtomicWorkspaceRepository(context.filesDir))
    val workspace = WorkspaceSession(Workspace(), executor, repository, WorkspaceValidator(registry, catalog),
        appExecutor = AppCommandExecutor(executor, curriculum, library, catalog, studyOutlines = outlines))
    val history = AtomicAssistantHistoryRepository(context.filesDir)
    val studyPreparation = dev.folio.runtime.study.StudyPreparationSession(library, outlines, jobs, catalog, workspace.state, scope)
    val indexing = dev.folio.runtime.study.DocumentIndexingSession(outlines,LocalDocumentChunks(context),mapper={
        if(live) dev.folio.remote.DirectGeminiDocumentMapper(requireNotNull(KeystoreProviderProfileStore(base).load()),
            base.assets.open("document-topic-map.md").bufferedReader().use { it.readText() })
        else object : DocumentMapper { override suspend fun map(chunk: DocumentChunk) = (chunk.firstPage until chunk.firstPage+chunk.pageCount).map {
            dev.folio.model.study.DocumentTopic("","Controlled page ${it+1}","Fixture map",it,it)
        } }
    },scope=scope)
    private val gate = InteractionGate()
    suspend fun seed() { library.add(pdf); repository.save(original); workspace.attachWriter(scope); workspace.restore(); assertNull(workspace.storageError.value) }
    fun assistant(provider: MentorProvider, documentStudy: Boolean = false) = AssistantSession(workspace,
        AppCapabilityContext(curriculum, sourceCatalog = catalog, documentStudyAvailable = documentStudy), library, catalog,
        provider = { provider }, effects = { effects -> effects.forEach { effect -> when (effect) {
            is AppEffect.EngineerPage -> jobs.start(effect.request)
            is AppEffect.AnalyzeStudy -> library.entries.value.first { it.id==effect.pdfId }.let { indexing.ensure(it.document,retry=true,requestId=effect.requestId.ifBlank{null}) }
            is AppEffect.PrepareStudy -> studyPreparation.ensure(effect.pdfId, effect.version)
            is AppEffect.CancelStudy -> studyPreparation.cancel(effect.pdfId)
            else -> error("Unconnected fixture effect")
        } } },
        interactionLocked = { gate.blocks(workspace.state.value, pdf.document.id) }, engineeringAvailable = true,
        liveContext = { buildJsonObject {
            put("interactionLocked", gate.blocks(workspace.state.value, pdf.document.id))
            if (documentStudy) put("documentStudy", dev.folio.runtime.study.StudyAssistantContext.build(workspace.state.value, library, outlines))
        } },
        history = history, effectAwaiter = if(documentStudy) dev.folio.runtime.study.PdfHarnessAwaiter(PagePreparationAwaiter(jobs,catalog),indexing,studyPreparation,library,outlines,workspace.state,catalog) else PagePreparationAwaiter(jobs,catalog))
    val mentorCalls = AtomicInteger()
    fun fakeProvider() = object : MentorProvider {
        override suspend fun respond(request: MentorRequest): MentorResponse {
            val first = mentorCalls.incrementAndGet() == 1
            val commands = if (first) listOf(AppCommand.EngineerPage(pdf.document.id, 0)) else {
                assertTrue(request.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean)
                assertTrue(request.sourceContext.getValue("page").toString().contains(marker))
                listOf(AppCommand.WorkspaceAction(WorkspaceCommand.CreateActivity("prepared-explanation", "prepared-explanation-view", ComponentKind.EXPLANATION,
                    SourceAnchor(pdf.document.id, 0, "prepared-p"), ActivityData.Explanation("Labelled explanation: amber circles belong to a group."))), AppCommand.Navigate(AppDestination.WORKSPACE))
            }
            return MentorResponse(requestId = request.requestId, message = "Labelled preparation fixture step", batch = AppCommandBatch(requestId = request.requestId,
                expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long, commands = commands), continueAfterExecution = first)
        }
    }
    suspend fun act(command: AppCommand) {
        val result = workspace.dispatchApp(AppCommandBatch(requestId = UUID.randomUUID().toString(), commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
        assertTrue(result.trace.error, result.trace.accepted)
        result.effects.forEach { effect -> when (effect) {
            is AppEffect.AnalyzeStudy -> library.entries.value.first { it.id==effect.pdfId }.let { indexing.ensure(it.document,retry=true,requestId=effect.requestId.ifBlank{null}) }
            is AppEffect.PrepareStudy -> studyPreparation.ensure(effect.pdfId, effect.version)
            is AppEffect.CancelStudy -> studyPreparation.cancel(effect.pdfId)
            else -> Unit
        } }
    }
    suspend fun close() { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
}
