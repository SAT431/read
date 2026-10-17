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

/** Saved credentials stay on device; only isolated labelled sample quiz state is changed. */
class LiveQuizAttemptsTest {
    @Test fun geminiStartsFreshThenShowsSavedAnswerAndReturnsToUntouchedCurrentDraft() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog(); val curriculum = Neet2026Catalog(); val registry = BuiltInComponents.registry()
        val executor = CommandExecutor(registry, catalog); val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val quiz = ActivityData.Quiz(listOf(Question("sample-q", "Labelled sample repeated-practice question", listOf("A", "B", "C", "D"), 0,
            "Labelled saved key", listOf(source))), answers = mapOf("sample-q" to 1), submitted = setOf("sample-q"),
            confidence = mapOf("sample-q" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 10)), checks = mapOf("sample-q" to QuizCheck(20)))
        val created = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("sample-quiz", "sample-quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.error, created.trace.accepted)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(created.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE)), executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        suspend fun ask(text: String) {
            assistant.ask(text); assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        fun data() = workspace.state.value.activities.getValue("sample-quiz").data as ActivityData.Quiz
        val before = System.currentTimeMillis()
        ask("Start another attempt of this quiz, keeping my current work saved read-only. Do not answer or check any question for me.")
        assertEquals(2, data().attemptNumber); assertTrue(data().answers.isEmpty()); assertTrue(data().submitted.isEmpty())
        val saved = data().pastAttempts.single()
        assertEquals(quiz.answers, saved.answers); assertEquals(quiz.confidence, saved.confidence); assertEquals(quiz.questions, saved.questions)
        assertTrue(saved.archivedByAssistant); assertTrue(saved.archivedAtEpochMillis >= before && saved.archivedAtEpochMillis <= System.currentTimeMillis())
        assertTrue(workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(AppCommand.SelectAnswer("sample-quiz", "sample-q", 0))),
            ExecutionPolicy(CommandActor.USER)).trace.accepted)
        val current = data().answers
        ask("Show my saved attempt 1 read-only. Leave my current attempt untouched.")
        assertEquals(QuizHistorySelection(1, "sample-q"), data().review)
        assertEquals(current, data().answers); assertEquals(saved, data().pastAttempts.single())
        ask("Return to my current quiz attempt. Keep all selected answers and earlier work unchanged.")
        assertNull(data().review); assertEquals(current, data().answers); assertTrue(data().submitted.isEmpty())
        assertEquals(saved, data().pastAttempts.single()); assertTrue(workspace.state.value.revisionItems.isEmpty())
        assertTrue(workspace.traces.value.all { it.accepted })
    }
}
