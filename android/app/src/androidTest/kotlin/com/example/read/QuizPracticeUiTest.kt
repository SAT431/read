package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.quiz.QuizRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated original sample practice; no learner workspace/provider calls. */
class QuizPracticeUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun multiQuestionPracticeRetainsChecksReviewsMistakesAndOpensExactQuestionSource() {
        val catalog = ArchitectureSources.catalog()
        val first = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p1")
        val second = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 1, "cell-p3")
        val questions = (0..2).map { index -> Question("q$index", "Practice question ${index + 1}",
            (0..3).map { "Q${index + 1} option ${('A'.code + it).toChar()}" }, index,
            "Practice explanation ${index + 1}", if (index == 1) listOf(second) else if (index == 2) listOf(first, second) else listOf(first)) }
        val registry = BuiltInComponents.registry()
        val commands = CommandExecutor(registry, catalog)
        val policy = ExecutionPolicy(CommandActor.USER)
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, first, ActivityData.Quiz(questions)))), policy).state
        val state = mutableStateOf(initial)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        fun dispatch(batch: List<AppCommand>) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "touch", commands = batch), policy)
            assertTrue(result.trace.error, result.trace.accepted)
            state.value = result.state
        }
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Restore saved practice") }
                if (state.value.activities.getValue("quiz").status == ActivityStatus.SUSPENDED)
                    TextButton({ dispatch(listOf(AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("quiz")))) }) { Text("Resume practice") }
                else QuizRenderer().Render(ComponentRenderContext(state.value.components.getValue("quiz-view"),
                    state.value.activities.getValue("quiz"), catalog, object : MediaRepository {
                        override suspend fun read(mediaId: String): ByteArray? = null
                    }, state.value.preferences,
                    { dispatch(it.map(AppCommand::WorkspaceAction)) }, { dispatch(listOf(LearningCommandBridge.command(it))) }))
            }
        } }
        compose.onNodeWithText("Correct option:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("quiz-option-q0-1").performScrollTo().performClick()
        compose.onNodeWithText("Correct option:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Incorrect answer").assertExists()
        compose.onNodeWithTag("quiz-option-q0-0").assertTextContains("correct", substring = true)
        compose.onNodeWithTag("quiz-option-q0-1").assertTextContains("your answer", substring = true)
        compose.onNodeWithText("Next question").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-option-q1-1").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Next unchecked").performScrollTo().performClick()
        compose.onNodeWithTag("quiz-option-q2-2").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Practice complete").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Checked 3 of 3 · 2 correct · 1 incorrect · 0 unchecked").assertExists()
        compose.onNodeWithText("Review source 2").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second, state.value.context.source) }
        compose.onNodeWithText("Resume practice").performScrollTo().performClick()
        compose.onNodeWithText("Review next mistake").performScrollTo().performClick()
        compose.onNodeWithText("Question 1 of 3").assertExists()
        compose.onNodeWithTag("quiz-option-q0-1").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Next question").performScrollTo().performClick()
        compose.onNodeWithText("Pause and review source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second, state.value.context.source) }
        compose.onNodeWithText("Resume practice").performScrollTo().performClick()
        compose.onNodeWithText("Restore saved practice").performScrollTo().performClick()
        compose.onNodeWithText("Question 2 of 3").assertExists()
        compose.onNodeWithTag("quiz-option-q1-1").assertIsSelected().assertIsNotEnabled()
        compose.runOnIdle {
            val quiz = state.value.activities.getValue("quiz").data as ActivityData.Quiz
            assertEquals(3, quiz.submitted.size)
            assertEquals(mapOf("q0" to 1, "q1" to 1, "q2" to 2), quiz.answers)
        }
    }
}
