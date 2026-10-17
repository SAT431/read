package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.learning.LearningOverviewScreen
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.practice.LearningMemory
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.quiz.QuizRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuizAttemptsUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun partialAttemptAndCurrentDraftSurviveReadOnlyHistoryMemoryResumeAndRecovery() {
        val catalog = ArchitectureSources.catalog(); val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog)
        val questions = (0..1).map { Question("q$it", "Fixture attempt question $it", listOf("A", "B", "C", "D"), 0, "Fixture key", listOf(source)) }
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, ActivityData.Quiz(questions)))), ExecutionPolicy(CommandActor.USER))
        assertTrue(initial.trace.error, initial.trace.accepted)
        val state = mutableStateOf(initial.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE)))
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val app = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        fun dispatch(vararg actions: AppCommand) {
            val result = app.execute(state.value, AppCommandBatch(requestId = "touch", commands = actions.toList()), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        fun data() = state.value.activities.getValue("quiz").data as ActivityData.Quiz
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize()) {
                Row {
                    TextButton({ dispatch(AppCommand.Navigate(AppDestination.LEARNING)) }) { Text("Fixture learning") }
                    TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover fixture") }
                }
                Box(Modifier.weight(1f)) {
                    if (state.value.navigation.destination == AppDestination.LEARNING) LearningOverviewScreen(state.value, catalog) { dispatch(it) }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        QuizRenderer().Render(ComponentRenderContext(state.value.components.getValue("quiz-view"), state.value.activities.getValue("quiz"), catalog,
                            object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                            { dispatch(*it.map(AppCommand::WorkspaceAction).toTypedArray()) }, { dispatch(LearningCommandBridge.command(it)) }, state.value.revisionItems))
                    }
                }
            }
        } }
        compose.onNodeWithTag("quiz-option-q0-1").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Next question").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-option-q1-2").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q1-LOW").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-restart-quiz").performScrollTo().performClick()
        compose.onNodeWithText("Keep working").performClick()
        compose.runOnIdle { assertEquals(1, data().attemptNumber); assertTrue(data().pastAttempts.isEmpty()) }
        compose.onNodeWithTag("quiz-restart-quiz").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confirm-restart").performClick()
        compose.runOnIdle {
            assertEquals(2, data().attemptNumber); assertTrue(data().answers.isEmpty())
            val saved = data().pastAttempts.single()
            assertEquals(mapOf("q0" to 1, "q1" to 2), saved.answers); assertEquals(setOf("q0"), saved.submitted)
            assertEquals(ConfidenceLevel.LOW, saved.confidence.getValue("q1").level)
        }
        compose.onNodeWithTag("quiz-option-q0-0").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q0-MEDIUM").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-history-quiz-1").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-history-heading").assertTextEquals("Saved attempt 1 · read-only")
        compose.onNodeWithTag("quiz-history-option-q1-2").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("This choice was not checked in the saved attempt.").assertExists()
        compose.onNodeWithTag("quiz-history-question-q0").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-history-option-q0-1").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Confidence: Very sure").assertExists()
        compose.onNodeWithText("Recover fixture").performClick()
        compose.onNodeWithTag("quiz-return-current").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-option-q0-0").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("quiz-confidence-q0-MEDIUM").assertIsSelected()
        compose.onNodeWithText("Fixture learning").performClick()
        compose.onNodeWithText("1 checked · 0 correct · 1 incorrect").assertExists()
        val old = LearningMemory.observations(state.value).single { it.questionId == "q0" && it.attemptNumber == 1 }
        compose.onNodeWithTag("learning-open-${old.id}").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-history-heading").assertTextEquals("Saved attempt 1 · read-only")
        compose.onNodeWithTag("quiz-return-current").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Fixture learning").performClick()
        compose.onNodeWithText("2 checked · 1 correct · 1 incorrect").assertExists()
        compose.runOnIdle {
            assertEquals(2, LearningMemory.summary(LearningMemory.observations(state.value)).checked)
            assertEquals(1, data().pastAttempts.single().checks.size)
            assertEquals(ConfidenceLevel.HIGH, data().pastAttempts.single().confidence.getValue("q0").level)
            assertEquals(ConfidenceLevel.MEDIUM, data().confidence.getValue("q0").level)
            assertTrue(state.value.revisionItems.isEmpty())
        }
    }
}
