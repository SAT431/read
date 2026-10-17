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
import dev.folio.runtime.reading.*
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveReadingConfirmationTest {
    @Test fun geminiRecordsOnlyExplicitSelectedSentenceReportThenClearsItWhenRequested() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>(); val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog(); val registry = BuiltInComponents.registry()
        val page = requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0)); val sentence = SentenceIndex().compile(page).first()
        val executor = CommandExecutor(registry, catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(ArchitectureActivities.initial(), executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        assertTrue(workspace.dispatchApp(AppCommandBatch(requestId = "fixture", commands = listOf(AppCommand.FocusSentence("reader", sentence.id))), ExecutionPolicy(CommandActor.USER)).trace.accepted)
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        suspend fun ask(text: String) {
            assistant.ask(text); assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        fun data() = workspace.state.value.activities.getValue("reader").data as ActivityData.Reader
        ask("I have read the selected sentence. Record my reading confirmation for that sentence only. Do not mark the whole passage read.")
        val record = data().readingConfirmations.values.single()
        assertEquals(sentence.id, record.sentenceId); assertTrue(record.byAssistant)
        assertEquals(0, ReadingConfirmations.progress(data(), page, catalog).confirmedPassages)
        ask("Clear the reading confirmation you just recorded. Do not change my source, notes or bookmarks.")
        assertTrue(data().readingConfirmations.isEmpty()); assertTrue(data().notes.isEmpty() && data().bookmarks.isEmpty())
        assertEquals(0, ReadingConfirmations.progress(data(), page, catalog).confirmedPassages)
    }
}
