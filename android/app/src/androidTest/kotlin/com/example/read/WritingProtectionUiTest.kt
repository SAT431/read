package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.ArchitectureSources
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.activity.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.study.reader.SourceAnnotationPanel
import dev.folio.ui.interaction.LocalInteractionGate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WritingProtectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun draftRecoversAfterReaderDisposalAndProtectionEndsAfterSave() {
        val catalog = ArchitectureSources.catalog()
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first()
        val state = mutableStateOf(Workspace(activities = mapOf("reader" to Activity("reader", sentence.source, ActivityData.Reader()))))
        val visible = mutableStateOf(true)
        val gate = InteractionGate()
        val reducer = LearningReducer(catalog)
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalInteractionGate provides gate) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton({
                    state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value))
                    visible.value = !visible.value
                }) { Text(if (visible.value) "Close reader" else "Reopen reader") }
                if (visible.value) {
                    val reader = state.value.activities.getValue("reader").data as ActivityData.Reader
                    SourceAnnotationPanel("reader", sentence.source, listOf(sentence), sentence.id,
                        reader.annotations.values.toList(), reader.annotationDrafts) { state.value = reducer.reduce(state.value, it) }
                }
            }
        } } }
        compose.onNodeWithText("Annotations (0)").performClick()
        compose.onNodeWithTag("note-draft-${sentence.source.elementId}").performTextInput("Unfinished note")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Close reader").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(gate.locked) }
        compose.onNodeWithText("Reopen reader").performClick()
        compose.onNodeWithTag("note-draft-${sentence.source.elementId}").assertTextContains("Unfinished note")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(gate.locked)
            val reader = state.value.activities.getValue("reader").data as ActivityData.Reader
            assertTrue(reader.annotationDrafts.isEmpty())
            assertEquals("Unfinished note", reader.annotations.values.single().text)
        }
        compose.onNodeWithText("Edit note").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Edit note")).performTextReplacement("Half-edited recovery draft")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Close reader").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(gate.locked) }
        compose.onNodeWithText("Reopen reader").performClick()
        compose.onNodeWithText("Annotations (1)").performClick()
        compose.onNode(hasSetTextAction() and hasText("Edit note")).assertTextContains("Half-edited recovery draft")
        compose.runOnIdle {
            assertTrue(gate.locked)
            assertEquals("Unfinished note", (state.value.activities.getValue("reader").data as ActivityData.Reader).annotations.values.single().text)
        }
        compose.onNodeWithText("Cancel edit").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(gate.locked)
            assertTrue((state.value.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts.isEmpty())
        }
    }
}
