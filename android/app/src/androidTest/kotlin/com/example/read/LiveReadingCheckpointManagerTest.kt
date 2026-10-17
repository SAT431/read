package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveReadingCheckpointManagerTest {
    @Test fun geminiShowsAndOpensSavedReadingTargetWithoutAddingAnotherReport() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>(); val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog(); val registry = BuiltInComponents.registry()
        val executor = CommandExecutor(registry, catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(ArchitectureActivities.initial(), executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first()
        val seed = workspace.dispatchApp(AppCommandBatch(requestId = "fixture", commands = listOf(
            AppCommand.ConfirmReading("reader", sentence.source, sentence.id, 0),
            AppCommand.WorkspaceAction(WorkspaceCommand.OpenPage(ArchitectureSources.DOCUMENT_ID, 1)))), ExecutionPolicy(CommandActor.USER))
        assertTrue(seed.trace.error, seed.trace.accepted)
        val record = (workspace.state.value.activities.getValue("reader").data as ActivityData.Reader).readingConfirmations.values.single()
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        assistant.ask("Show my saved reading-checkpoint list and open the saved checkpoint. Do not create, refresh or clear any reading report.")
        assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        val reader = workspace.state.value.activities.getValue("reader").data as ActivityData.Reader
        assertTrue(reader.readingCheckpointsVisible)
        assertEquals(sentence.source, workspace.state.value.context.source); assertEquals(sentence.id, reader.selectedSentence)
        assertEquals(mapOf(record.id to record), reader.readingConfirmations); assertEquals(1L, reader.readingRevision)
        assertTrue(reader.bookmarks.isEmpty() && reader.annotations.isEmpty())
    }
}
