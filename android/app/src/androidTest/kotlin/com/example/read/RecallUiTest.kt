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
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.recall.RecallRenderer
import dev.folio.ui.component.ComponentRenderContext
import dev.folio.ui.interaction.LocalInteractionGate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecallUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun privateDraftSurvivesPauseRecoveryThenRevealRatingAndRetryRetainReportedHistory() {
        val catalog = ArchitectureSources.catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog)
        val policy = ExecutionPolicy(CommandActor.USER)
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "create", commands = listOf(
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, source,
                ActivityData.Recall("Fixture recall prompt", "Fixture reference answer", listOf(source))))), policy)
        assertTrue(initial.trace.error, initial.trace.accepted)
        val state = mutableStateOf(initial.state)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        val gate = InteractionGate()
        fun dispatch(batch: List<AppCommand>) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "touch", commands = batch), policy)
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalInteractionGate provides gate) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover recall snapshot") }
                if (state.value.activities.getValue("recall").status == ActivityStatus.SUSPENDED)
                    TextButton({ dispatch(listOf(AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("recall")))) }) { Text("Resume recall") }
                else RecallRenderer().Render(ComponentRenderContext(state.value.components.getValue("recall-view"),
                    state.value.activities.getValue("recall"), catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                    { dispatch(it.map(AppCommand::WorkspaceAction)) }, { dispatch(listOf(LearningCommandBridge.command(it))) }))
            }
        } } }
        compose.onNodeWithTag("recall-reference-recall").assertDoesNotExist()
        compose.onNodeWithTag("recall-rating-RECALLED").assertDoesNotExist()
        compose.onNodeWithTag("recall-response-recall").performTextReplacement("My unfinished recalled response")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Pause and review source").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(gate.locked); assertEquals(source, state.value.context.source) }
        compose.onNodeWithText("Recover recall snapshot").performScrollTo().performClick()
        compose.onNodeWithText("Resume recall").performScrollTo().performClick()
        compose.onNodeWithTag("recall-response-recall").performScrollTo().assertTextContains("My unfinished recalled response")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Reveal reference answer").performScrollTo().performClick()
        compose.onNodeWithTag("recall-reference-recall").assertTextEquals("Fixture reference answer")
        compose.runOnIdle { assertFalse(gate.locked) }
        compose.onNodeWithTag("recall-rating-PARTIAL").performScrollTo().performClick()
        compose.onNodeWithTag("recall-rating-PARTIAL").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Recover recall snapshot").performScrollTo().performClick()
        compose.onNodeWithTag("recall-rating-PARTIAL").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithTag("recall-reference-recall").assertDoesNotExist()
        compose.runOnIdle {
            val recall = state.value.activities.getValue("recall").data as ActivityData.Recall
            assertEquals("", recall.response); assertFalse(recall.revealed); assertNull(recall.rating)
            assertEquals("My unfinished recalled response", recall.pastAttempts.single().response)
            assertEquals(RecallRating.PARTIAL, recall.pastAttempts.single().rating)
            assertTrue(recall.pastAttempts.single().ratedAtEpochMillis > 0)
            assertTrue(state.value.sourceCorrections.isEmpty())
        }
    }
}
