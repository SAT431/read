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
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.quiz.QuizRenderer
import dev.folio.study.recall.RecallRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RevisionSuggestionUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun checkedAnswersAndReportedRecallHistoryOfferAdjustableSuggestionsThroughSharedActions() {
        val catalog = ArchitectureSources.catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val executor = CommandExecutor(BuiltInComponents.registry(), catalog)
        val policy = ExecutionPolicy(CommandActor.USER)
        val initial = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, source,
                ActivityData.Recall("Fixture recall prompt", "Fixture reference", listOf(source), response = "Retained fixture response")),
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, ActivityData.Quiz(listOf(
                Question("q0", "Fixture question", listOf("A", "B", "C", "D"), 0, "Fixture explanation", listOf(source))))))), policy)
        assertTrue(initial.trace.error, initial.trace.accepted)
        val state = mutableStateOf(initial.state)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val app = AppCommandExecutor(executor, Neet2026Catalog(), library, catalog)
        fun dispatch(vararg commands: AppCommand) {
            val result = app.execute(state.value, AppCommandBatch(requestId = "touch", commands = commands.toList()), policy)
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize()) {
                Row {
                    TextButton({ dispatch(AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("recall"))) }) { Text("Fixture recall") }
                    TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover fixture") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    val activity = state.value.activities.getValue(state.value.context.focusedActivityId ?: "quiz")
                    val component = state.value.components.values.first { it.activityId == activity.id }
                    val context = ComponentRenderContext(component, activity, catalog, object : MediaRepository {
                        override suspend fun read(mediaId: String): ByteArray? = null
                    }, state.value.preferences, { dispatch(*it.map(AppCommand::WorkspaceAction).toTypedArray()) },
                        { dispatch(LearningCommandBridge.command(it)) }, state.value.revisionItems)
                    if (activity.data is ActivityData.Quiz) QuizRenderer().Render(context) else RecallRenderer().Render(context)
                }
            }
        } }
        compose.onNodeWithTag("revision-suggestion-quiz-q0").assertDoesNotExist()
        compose.onNodeWithTag("quiz-option-q0-1").performScrollTo().performClick()
        compose.onNodeWithTag("revision-suggestion-quiz-q0").assertDoesNotExist()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Suggested review: in 1 day").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("revision-suggestion-quiz-q0").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RevisionReason.QUIZ_INCORRECT, state.value.revisionItems.values.single().suggestion!!.reason) }
        compose.onNodeWithTag("revision-schedule-quiz-q0-7").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(state.value.revisionItems.values.single().suggestion)
            val quiz = state.value.activities.getValue("quiz").data as ActivityData.Quiz
            assertEquals(1, quiz.answers["q0"]); assertTrue("q0" in quiz.submitted)
        }
        compose.onNodeWithText("Fixture recall").performClick()
        compose.onNodeWithTag("revision-suggestion-recall-recall").assertDoesNotExist()
        compose.onNodeWithText("Reveal reference answer").performScrollTo().performClick()
        compose.onNodeWithTag("recall-rating-FORGOT").performScrollTo().performClick()
        compose.onNodeWithTag("revision-suggestion-recall-recall").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, state.value.revisionItems.values.single { it.activityId == "recall" }.suggestion!!.delayDays) }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithTag("revision-suggestion-recall-recall").assertDoesNotExist()
        repeat(2) {
            compose.onNodeWithText("Reveal reference answer").performScrollTo().performClick()
            compose.onNodeWithTag("recall-rating-RECALLED").performScrollTo().performClick()
            compose.onNodeWithTag("revision-suggestion-recall-recall").performScrollTo().performClick()
            if (it == 0) compose.onNodeWithText("Try again").performScrollTo().performClick()
        }
        compose.onNodeWithText("Recover fixture").performClick()
        compose.runOnIdle {
            val item = state.value.revisionItems.values.single { it.activityId == "recall" }
            assertEquals(14, item.suggestion!!.delayDays); assertEquals(2, item.suggestion!!.recalledStreak)
            val recall = state.value.activities.getValue("recall").data as ActivityData.Recall
            assertEquals(2, recall.pastAttempts.size)
            assertEquals("Retained fixture response", recall.pastAttempts.first().response)
            assertFalse(item.done); assertTrue(item.completions.isEmpty())
        }
    }
}
