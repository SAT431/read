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

/** Live saved-key requests use isolated sample learning data, never the learner's answers. */
class LiveLearningMemoryTest {
    @Test fun geminiRecordsExplicitConfidenceThenRetrievesAndOpensTheNativeMistakeObservation() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog()
        val registry = BuiltInComponents.registry(); val executor = CommandExecutor(registry, catalog)
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val quiz = ActivityData.Quiz(listOf(Question("sample-q", "Labelled sample question", listOf("A", "B", "C", "D"), 0,
            "Labelled sample key", listOf(source))), answers = mapOf("sample-q" to 1))
        val created = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("sample-quiz", "sample-quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.error, created.trace.accepted)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(created.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE)), executor,
            object : WorkspaceRepository {
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
        ask("I am very sure about my selected answer. Record that as my confidence only. Keep my answer unchanged and do not check it.")
        val reported = workspace.state.value.activities.getValue("sample-quiz").data as ActivityData.Quiz
        assertEquals(quiz.answers, reported.answers); assertTrue(reported.submitted.isEmpty())
        assertEquals(ConfidenceLevel.HIGH, reported.confidence.getValue("sample-q").level)
        assertTrue(reported.confidence.getValue("sample-q").byAssistant)
        val checked = workspace.dispatchApp(AppCommandBatch(requestId = "touch-check", expectedRevision = workspace.state.value.revision,
            commands = listOf(AppCommand.SubmitAnswer("sample-quiz", "sample-q"))), ExecutionPolicy(CommandActor.USER))
        assertTrue(checked.trace.error, checked.trace.accepted)
        val locked = workspace.state.value.activities.getValue("sample-quiz").data
        ask("Show my confident mistakes in Learning memory.")
        assertEquals(AppDestination.LEARNING, workspace.state.value.navigation.destination)
        assertEquals(LearningFilter.CONFIDENT_MISTAKES, workspace.state.value.navigation.learningFilter)
        ask("Open that saved learning observation's question with its source context. Keep all recorded answers and confidence unchanged.")
        assertEquals(AppDestination.WORKSPACE, workspace.state.value.navigation.destination)
        assertEquals("sample-quiz", workspace.state.value.context.focusedActivityId)
        assertEquals(source, workspace.state.value.context.source)
        assertEquals(locked, workspace.state.value.activities.getValue("sample-quiz").data)
        assertTrue(workspace.state.value.revisionItems.isEmpty())
        assertTrue(workspace.traces.value.all { it.accepted })
    }
}
