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
import dev.folio.model.workspace.Workspace
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

/** Real Gemini; one isolated checked sample answer, no learner records modified. */
class LiveRevisionSuggestionTest {
    @Test fun geminiAcceptsTheNativeSuggestedIntervalWithoutInventingEvidenceOrChangingAnswers() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val registry = BuiltInComponents.registry(); val executor = CommandExecutor(registry, catalog)
        val data = ActivityData.Quiz(listOf(Question("sample-question", "Labelled sample question", listOf("A", "B", "C", "D"),
            0, "Labelled sample reference", listOf(source))), answers = mapOf("sample-question" to 1), submitted = setOf("sample-question"))
        val created = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("sample-quiz", "sample-quiz-view", ComponentKind.QUIZ, source, data))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.error, created.trace.accepted)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(created.state, executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        val before = System.currentTimeMillis()
        assistant.ask("Use the app's suggested review interval to schedule this existing checked quiz question. Keep its answer unchanged and do not mark the review complete.")
        assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        val item = workspace.state.value.revisionItems.values.single()
        assertEquals("sample-quiz", item.activityId); assertEquals("sample-question", item.questionId)
        assertEquals(RevisionReason.QUIZ_INCORRECT, item.suggestion!!.reason)
        assertEquals(1, item.suggestion!!.delayDays); assertTrue(item.scheduledByAssistant)
        assertTrue(item.dueAtEpochMillis >= before + 86_400_000L)
        assertTrue(item.dueAtEpochMillis <= System.currentTimeMillis() + 86_400_000L)
        assertEquals(created.state.activities, workspace.state.value.activities)
        assertFalse(item.done); assertTrue(item.completions.isEmpty())
        assertTrue(workspace.traces.value.first().commands.contains("revision.accept_suggestion"))
    }
}
