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
import dev.folio.study.recall.RecallRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LearningMemoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun optionalPrecheckConfidenceAndSavedMemoryFiltersResumeExactWorkWithoutRewritingHistory() {
        val catalog = ArchitectureSources.catalog(); val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog); val policy = ExecutionPolicy(CommandActor.USER)
        val questions = (0..1).map { index -> Question("q$index", "Fixture memory question $index", listOf("A", "B", "C", "D"), 0, "Fixture reference", listOf(source)) }
        val recall = ActivityData.Recall("Fixture recall prompt", "Fixture recall reference", listOf(source), response = "Retained current response",
            revealed = true, rating = RecallRating.PARTIAL, ratedAtEpochMillis = 20,
            pastAttempts = listOf(RecallAttempt("Earlier retained response", RecallRating.FORGOT, 10, true, true)))
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, source, recall),
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, ActivityData.Quiz(questions)))), policy)
        assertTrue(initial.trace.error, initial.trace.accepted)
        val state = mutableStateOf(initial.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE)))
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        fun dispatch(vararg actions: AppCommand) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "touch", commands = actions.toList()), policy)
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize()) {
                Row {
                    TextButton({ dispatch(AppCommand.Navigate(AppDestination.LEARNING)) }) { Text("Fixture learning") }
                    TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover fixture") }
                }
                Box(Modifier.weight(1f)) {
                    if (state.value.navigation.destination == AppDestination.LEARNING)
                        LearningOverviewScreen(state.value, catalog) { dispatch(it) }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        val activity = state.value.activities.getValue(state.value.context.focusedActivityId ?: "quiz")
                        val view = state.value.components.values.first { it.activityId == activity.id }
                        val context = ComponentRenderContext(view, activity, catalog, object : MediaRepository {
                            override suspend fun read(mediaId: String): ByteArray? = null
                        }, state.value.preferences, { dispatch(*it.map(AppCommand::WorkspaceAction).toTypedArray()) },
                            { dispatch(LearningCommandBridge.command(it)) }, state.value.revisionItems)
                        if (activity.data is ActivityData.Quiz) QuizRenderer().Render(context) else RecallRenderer().Render(context)
                    }
                }
            }
        } }
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").assertIsNotEnabled()
        compose.onNodeWithTag("quiz-option-q0-1").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").assertIsSelected()
        compose.onNodeWithTag("quiz-option-q0-2").performScrollTo().performClick()
        compose.runOnIdle { assertTrue((state.value.activities.getValue("quiz").data as ActivityData.Quiz).confidence.isEmpty()) }
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q0-HIGH").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Next question").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-option-q1-0").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-confidence-q1-LOW").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Fixture learning").performClick()
        compose.onNodeWithText("2 checked · 1 correct · 1 incorrect").assertExists()
        compose.onNodeWithTag("learning-filter-CONFIDENT_MISTAKES").performScrollTo().performClick()
        compose.onNodeWithTag("learning-count").assertTextEquals("1 saved observation")
        compose.onNodeWithText("Fixture memory question 0").assertExists()
        compose.onNodeWithTag("learning-filter-UNCERTAIN_CORRECT").performScrollTo().performClick()
        compose.onNodeWithText("Confidence: Unsure").assertExists()
        compose.onNodeWithText("Recover fixture").performClick()
        val correct = LearningMemory.observations(state.value).single { it.questionId == "q1" }
        compose.onNodeWithTag("learning-open-${correct.id}").performScrollTo().performClick()
        compose.onNodeWithText("Question 2 of 2").assertExists()
        compose.onNodeWithTag("quiz-confidence-q1-LOW").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Fixture learning").performClick()
        compose.onNodeWithTag("learning-filter-RECALL_REPORTS").performScrollTo().performClick()
        compose.onNodeWithTag("learning-count").assertTextEquals("2 saved observations")
        val historical = LearningMemory.observations(state.value).single { it.questionId == null && it.attemptNumber == 1 }
        compose.onNodeWithTag("learning-open-${historical.id}").performScrollTo().performClick()
        compose.onNodeWithTag("recall-response-recall").performScrollTo().assertTextContains("Retained current response")
        compose.runOnIdle {
            assertEquals(recall, state.value.activities.getValue("recall").data)
            val saved = state.value.activities.getValue("quiz").data as ActivityData.Quiz
            assertEquals(setOf("q0", "q1"), saved.submitted)
            assertEquals(ConfidenceLevel.HIGH, saved.confidence.getValue("q0").level)
            assertEquals(ConfidenceLevel.LOW, saved.confidence.getValue("q1").level)
            assertTrue(state.value.revisionItems.isEmpty())
        }
    }
}
