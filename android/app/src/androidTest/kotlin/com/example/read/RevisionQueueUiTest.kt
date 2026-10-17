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
import dev.folio.library.revision.RevisionQueueScreen
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
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.quiz.QuizRenderer
import dev.folio.study.recall.RecallRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RevisionQueueUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun scheduleReopenCompleteRescheduleRecoverAndRemovePreserveQuizAndRecallWork() {
        val catalog = ArchitectureSources.catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog)
        val policy = ExecutionPolicy(CommandActor.USER)
        val quiz = ActivityData.Quiz(listOf(
            Question("q0", "First fixture question", listOf("A", "B", "C", "D"), 0, "Reference", listOf(source)),
            Question("q1", "Second fixture question", listOf("A", "B", "C", "D"), 1, "Reference", listOf(source))),
            index = 1, answers = mapOf("q0" to 2), submitted = setOf("q0"))
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "create", commands = listOf(
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, source,
                ActivityData.Recall("Fixture recall prompt", "Fixture reference", listOf(source), response = "Private retained response")),
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, quiz))), policy)
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
                    TextButton({ dispatch(AppCommand.Navigate(AppDestination.REVISION)) }) { Text("Fixture queue") }
                    TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover fixture") }
                    TextButton({ dispatch(AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("recall")), AppCommand.Navigate(AppDestination.WORKSPACE)) }) { Text("Fixture recall") }
                }
                Box(Modifier.weight(1f)) {
                    if (state.value.navigation.destination == AppDestination.REVISION)
                        RevisionQueueScreen(state.value, catalog, { dispatch(it) }, System.currentTimeMillis() + 5000)
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        val activity = state.value.activities.getValue(state.value.context.focusedActivityId ?: "quiz")
                        val component = state.value.components.values.first { it.activityId == activity.id }
                        val render = ComponentRenderContext(component, activity, catalog,
                            object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                            { dispatch(*it.map(AppCommand::WorkspaceAction).toTypedArray()) },
                            { dispatch(LearningCommandBridge.command(it)) }, state.value.revisionItems)
                        if (activity.data is ActivityData.Quiz) QuizRenderer().Render(render) else RecallRenderer().Render(render)
                    }
                }
            }
        } }
        compose.onNodeWithTag("revision-schedule-quiz-q1-0").performScrollTo().performClick()
        compose.onNodeWithText("Fixture queue").performClick()
        compose.onNodeWithText("1 due · 0 upcoming · 0 completed").assertExists()
        val quizId = state.value.revisionItems.values.single().id
        compose.onNodeWithTag("revision-open-$quizId").performScrollTo().performClick()
        compose.onNodeWithText("Second fixture question").assertExists()
        compose.runOnIdle {
            assertEquals(quiz, state.value.activities.getValue("quiz").data)
            assertFalse(state.value.revisionItems.getValue(quizId).done)
        }
        compose.onNodeWithText("Fixture queue").performClick()
        compose.onNodeWithTag("revision-complete-$quizId").performScrollTo().performClick()
        compose.onNodeWithText("0 due · 0 upcoming · 1 completed").assertExists()
        compose.onNodeWithTag("revision-reschedule-$quizId-3").performScrollTo().performClick()
        compose.onNodeWithText("0 due · 1 upcoming · 0 completed").assertExists()
        compose.onNodeWithText("Recover fixture").performClick()
        compose.runOnIdle { assertEquals(1, state.value.revisionItems.getValue(quizId).completions.size) }
        compose.onNodeWithText("Fixture recall").performClick()
        compose.onNodeWithTag("revision-schedule-recall-recall-0").performScrollTo().performClick()
        compose.onNodeWithText("Fixture queue").performClick()
        compose.onNodeWithText("1 due · 1 upcoming · 0 completed").assertExists()
        val recallId = state.value.revisionItems.values.single { it.activityId == "recall" }.id
        compose.onNodeWithTag("revision-open-$recallId").performScrollTo().performClick()
        compose.onNodeWithTag("recall-response-recall").performScrollTo().assertTextContains("Private retained response")
        compose.onNodeWithTag("recall-reference-recall").assertDoesNotExist()
        compose.onNodeWithText("Fixture queue").performClick()
        compose.onNodeWithTag("revision-remove-$recallId").performScrollTo().performClick()
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertTrue(recallId in state.value.revisionItems) }
        compose.onNodeWithTag("revision-remove-$recallId").performScrollTo().performClick()
        compose.onNodeWithTag("revision-confirm-remove").performClick()
        compose.runOnIdle {
            assertFalse(recallId in state.value.revisionItems)
            assertEquals(initial.state.activities.getValue("recall").data, state.value.activities.getValue("recall").data)
            assertEquals(quiz, state.value.activities.getValue("quiz").data)
            assertEquals(1, state.value.revisionItems.getValue(quizId).completions.size)
            assertTrue(state.value.sourceCorrections.isEmpty())
        }
    }
}
