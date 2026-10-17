package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.workspace.*
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real saved-key Gemini requests with isolated app state: no learner PDF/history is changed. */
class LiveNavigationTest {
    @Test fun automaticTranscriptOpensSubjectThenKnownPdfAndAdvancesItsPage() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val curriculum = Neet2026Catalog()
        val chapter = curriculum.chapters().first { it.subject == SubjectId.BOTANY }
        // Navigation metadata is deliberately labelled; this test does not render or process PDF bytes.
        val document = SourceDocument("live-navigation-fixture", "Navigation Test PDF", 3, "test-checksum", null)
        val entry = LibraryPdf("navigation-pdf", chapter.id, document, 0)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(listOf(entry))
            override suspend fun load() {}
        }
        val catalog = InMemorySourceCatalog(listOf(document), emptyList(), emptyList())
        val registry = BuiltInComponents.registry()
        val commands = CommandExecutor(registry, catalog)
        val workspace = WorkspaceSession(Workspace(), commands, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(commands, curriculum, library, catalog))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue("Unexpected OS effect", it.isEmpty()) })
        suspend fun speakTranscript(text: String) {
            var submitted: Job? = null
            val voice = VoiceTranscriptRouter { submitted = launch { assistant.ask(it) }; true }
            assertTrue(voice.received(text, automaticAtStart = true, automaticNow = true).transcript.isEmpty())
            requireNotNull(submitted).join()
            assertEquals("Command '$text' failed: ${assistant.state.value.error}", AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        speakTranscript("Open Botany")
        assertEquals(AppDestination.SUBJECT, workspace.state.value.navigation.destination)
        assertEquals(SubjectId.BOTANY, workspace.state.value.navigation.subject)
        speakTranscript("Open the Navigation Test PDF in the original PDF viewer")
        assertEquals(AppDestination.PDF, workspace.state.value.navigation.destination)
        assertEquals(entry.id, workspace.state.value.navigation.pdfId)
        assertEquals(0, workspace.state.value.navigation.pdfPageIndex)
        speakTranscript("Go to the next page")
        assertEquals(AppDestination.PDF, workspace.state.value.navigation.destination)
        assertEquals(entry.id, workspace.state.value.navigation.pdfId)
        assertEquals(1, workspace.state.value.navigation.pdfPageIndex)
        assertTrue(workspace.traces.value.isNotEmpty())
        assertTrue(workspace.traces.value.all { it.accepted })
    }
}
