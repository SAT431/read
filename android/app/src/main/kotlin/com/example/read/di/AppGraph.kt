package com.example.read.di

import android.content.Context
import com.example.read.fixture.ArchitectureActivities
import com.example.read.fixture.ArchitectureSources
import dev.folio.local.snapshot.AtomicWorkspaceRepository
import dev.folio.local.media.LocalMediaRepository
import java.io.File
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.layout.LayoutSolver
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import dev.folio.study.registry.StudyRenderers
import dev.folio.ui.component.RendererRegistry
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.library.LocalPdfLibrary
import dev.folio.local.catalog.LibrarySourceCatalog
import dev.folio.local.pdf.PdfImporter
import dev.folio.local.pdf.NativePdfTools
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.model.workspace.Workspace
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.local.pdf.LocalPdfPageExtraction
import com.example.read.engineering.LocalPageEngineer
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.port.*
import dev.folio.remote.*
import dev.folio.runtime.engineering.EngineeringCoordinator
import dev.folio.model.assistant.VoiceInputState
import kotlinx.coroutines.flow.MutableStateFlow

/** Only the composition root knows concrete feature and storage implementations. */
class AppGraph(context: Context) {
    val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val voiceInputState = MutableStateFlow(VoiceInputState())
    val interactions = dev.folio.runtime.interaction.InteractionGate()
    val interactionLocked get() = interactions.blocks(session.state.value,
        library.entries.value.firstOrNull { it.id == session.state.value.navigation.pdfId }?.document?.id)
    val curriculum = Neet2026Catalog()
    val database = FolioDatabase.open(context)
    val providerProfiles = KeystoreProviderProfileStore(context)
    val assistantHistory = dev.folio.local.snapshot.AtomicAssistantHistoryRepository(context.noBackupFilesDir)
    val mentorPrompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
        context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
    val library = LocalPdfLibrary(context.filesDir, database.library())
    val pages = RoomPageRepository(database.pages(), dev.folio.local.source.PrivateSourceCacheBackup(File(context.noBackupFilesDir, "source-cache-backups")))
    val cacheRepair = dev.folio.runtime.source.SourceCacheRepairSession(pages)
    private var correctionRecords: () -> Map<String, dev.folio.model.source.SourceCorrection> = { emptyMap() }
    val catalog = dev.folio.runtime.engineering.CorrectedSourceCatalog(
        LibrarySourceCatalog(library, curriculum, ArchitectureSources.catalog(), pages)) { correctionRecords() }
    val importer = PdfImporter(context.contentResolver, context.filesDir, library)
    val pdfTools = NativePdfTools(context.filesDir, catalog)
    val media = LocalMediaRepository(File(context.filesDir, "source-assets"))
    val engineering = EngineeringCoordinator(LocalPageEngineer(providerProfiles,
        LocalPdfPageExtraction(context, catalog, pdfTools),
        context.assets.open("native-page-draft.md").bufferedReader().use { it.readText() }, pages::stageFidelity), pages,
        cachedUpgrade=dev.folio.runtime.engineering.FigureAssetUpgrade(pdfTools)::upgrade)
    val pagePreparation = dev.folio.runtime.engineering.PagePreparationJobs(engineering, persistenceScope)
    val documentOutlines = dev.folio.local.study.AtomicDocumentOutlines(File(context.filesDir, "document-maps"))
    val documentIndexing = dev.folio.runtime.study.DocumentIndexingSession(documentOutlines,
        dev.folio.local.pdf.LocalDocumentChunks(context), mapper = {
            DirectGeminiDocumentMapper(providerProfiles.load() ?: error("Configure Gemini"),
                context.assets.open("document-topic-map.md").bufferedReader().use { it.readText() })
        }, scope = persistenceScope)
    val capabilities = BuiltInComponents.registry()
    val renderers = RendererRegistry(capabilities, StudyRenderers.all())
    val layoutSolver = LayoutSolver(capabilities)
    private val commands = CommandExecutor(capabilities, catalog)
    val session = WorkspaceSession(Workspace(), commands,
        RoomWorkspaceRepository(database.workspace(), AtomicWorkspaceRepository(context.filesDir)), WorkspaceValidator(capabilities, catalog),
        appExecutor = AppCommandExecutor(commands, curriculum, library, catalog, cacheRepair, documentOutlines),
        timerClock = { android.os.SystemClock.elapsedRealtime() }, initialForeground = false)
    val search = dev.folio.runtime.search.StudySearchSession(persistenceScope, session.state, library, catalog, pages.revision, curriculum)
    val studyPreparation = dev.folio.runtime.study.StudyPreparationSession(library, documentOutlines, pagePreparation,
        catalog, session.state, persistenceScope)
    val preparationAwaiter = dev.folio.runtime.study.PdfHarnessAwaiter(
        dev.folio.runtime.engineering.PagePreparationAwaiter(pagePreparation,catalog),documentIndexing,studyPreparation,library,documentOutlines,session.state,catalog)
    init { correctionRecords = { session.state.value.sourceCorrections }; session.attachWriter(persistenceScope) }
}
