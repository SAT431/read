package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.*
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in Gemini calls; isolated labelled sample state, never learner answers/PDFs. */
class LiveRevisionQueueTest {
    @Test fun geminiSchedulesOpensQueueAndRecordsOnlyExplicitReportedReviewCompletion() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        assertEquals(GeminiModels.CURRENT_FLASH, profile.geminiModel)
        val catalog = ArchitectureSources.catalog()
        val curriculum = Neet2026Catalog()
        val registry = BuiltInComponents.registry()
        val executor = CommandExecutor(registry, catalog)
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val initial = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("revision-recall", "revision-recall-view", ComponentKind.RECALL, source,
                ActivityData.Recall("Labelled sample: recall the endoplasmic reticulum", "Sample reference", listOf(source))))),
            ExecutionPolicy(CommandActor.USER)).state
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(initial, executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        suspend fun ask(text: String) {
            assistant.ask(text)
            assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        val before = System.currentTimeMillis()
        ask("Schedule the existing active recall card for review in 3 days. Keep its answer and reference unchanged. Do not create a new card or mark anything complete.")
        val item = workspace.state.value.revisionItems.values.single()
        assertEquals("revision-recall", item.activityId); assertNull(item.questionId)
        assertTrue(item.dueAtEpochMillis >= before + 3 * 86_400_000L)
        assertTrue(item.dueAtEpochMillis <= System.currentTimeMillis() + 3 * 86_400_000L)
        assertTrue(item.scheduledByAssistant); assertFalse(item.done); assertTrue(item.completions.isEmpty())
        assertEquals(initial.activities, workspace.state.value.activities)
        ask("Open the Revision queue tab. Do not open a card or change the schedule.")
        assertEquals(AppDestination.REVISION, workspace.state.value.navigation.destination)
        assertEquals(item, workspace.state.value.revisionItems.getValue(item.id))
        ask("I have finished reviewing that scheduled recall card. Mark its review complete; keep the card and its answers unchanged and do not set another schedule.")
        val completed = workspace.state.value.revisionItems.getValue(item.id)
        assertTrue(completed.done); assertEquals(item.version + 1, completed.version)
        assertEquals(1, completed.completions.size); assertTrue(completed.completions.single().byAssistant)
        assertEquals(initial.activities, workspace.state.value.activities)
        assertTrue(workspace.traces.value.all { it.accepted })
    }
}
