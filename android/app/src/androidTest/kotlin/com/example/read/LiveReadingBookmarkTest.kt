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

class LiveReadingBookmarkTest {
    @Test fun geminiSavesSelectedSentenceReopensItFromAnotherPageAndRemovesOnlyRequestedRecord() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog(); val registry = BuiltInComponents.registry()
        val executor = CommandExecutor(registry, catalog)
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first()
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(ArchitectureActivities.initial(), executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        suspend fun act(command: AppCommand) {
            val result = workspace.dispatchApp(AppCommandBatch(requestId = "fixture", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted)
        }
        act(AppCommand.FocusSentence("reader", sentence.id))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        suspend fun ask(text: String) {
            assistant.ask(text); assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        fun data() = workspace.state.value.activities.getValue("reader").data as ActivityData.Reader
        ask("Save a reading bookmark for my selected source sentence. Do not create notes, explanations or practice activities.")
        val bookmark = data().bookmarks.values.single()
        assertEquals(sentence.id, bookmark.sentenceId); assertTrue(bookmark.byAssistant)
        act(AppCommand.WorkspaceAction(WorkspaceCommand.OpenPage(ArchitectureSources.DOCUMENT_ID, 1)))
        ask("Open my saved reading bookmark. Keep its original sentence target unchanged.")
        assertEquals(sentence.source, workspace.state.value.context.source)
        assertEquals(sentence.id, data().selectedSentence); assertEquals(bookmark, data().bookmarks.getValue(bookmark.id))
        ask("Remove that saved reading bookmark. Leave the source text and my notes unchanged.")
        assertTrue(data().bookmarks.isEmpty()); assertTrue(data().annotations.isEmpty() && data().notes.isEmpty())
        assertEquals(1, workspace.state.value.activities.size)
    }
}
