package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.ArchitectureSources
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.activity.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningReducer
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.study.reader.SourceAnnotationPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceAnnotationUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun sourceNoteAndHighlightSurviveReopenThenEditAndDeleteThroughNativeControls() {
        val catalog = ArchitectureSources.catalog()
        val page = requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))
        val sentence = SentenceIndex().compile(page).first()
        val sentences = SentenceIndex().compile(page).filter { it.source == sentence.source }
        val selected = mutableStateOf(sentence.id)
        val state = mutableStateOf(Workspace(activities = mapOf("reader" to Activity("reader", sentence.source, ActivityData.Reader()))))
        val visible = mutableStateOf(true)
        val reducer = LearningReducer(catalog)
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton({
                    state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value))
                    visible.value = !visible.value
                }) { Text(if (visible.value) "Close reader fixture" else "Reopen reader fixture") }
                if (visible.value) SourceAnnotationPanel("reader", sentence.source, sentences, selected.value,
                    (state.value.activities.getValue("reader").data as ActivityData.Reader).annotations.values.toList()) {
                    state.value = reducer.reduce(state.value, it)
                }
            }
        } }
        compose.onNodeWithText("Annotations (0)").performClick()
        compose.onNodeWithText("highlight").performClick()
        compose.onNodeWithTag("note-draft-${sentence.source.elementId}").performTextInput("Review membrane structure")
        compose.runOnIdle { selected.value = sentences.last().id }
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        compose.onNodeWithText("Review membrane structure").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val notes = (state.value.activities.getValue("reader").data as ActivityData.Reader).annotations.values
            assertEquals(sentence.id, notes.single { it.kind == AnnotationKind.NOTE }.sentenceId)
        }
        compose.onNodeWithText("Close reader fixture").performScrollTo().performClick()
        compose.onNodeWithText("Reopen reader fixture").performClick()
        compose.onNodeWithText("Annotations (2)").performClick()
        compose.onNodeWithText("Edit note").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Edit note")).performTextReplacement("Revised note")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("Revised note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete note").performScrollTo().performClick()
        compose.runOnIdle {
            val annotations = (state.value.activities.getValue("reader").data as ActivityData.Reader).annotations.values
            assertEquals(1, annotations.size)
            assertEquals(AnnotationKind.HIGHLIGHT, annotations.single().kind)
            assertEquals(sentence.id, annotations.single().sentenceId)
            assertEquals(page, catalog.page(page.documentId, page.pageIndex))
        }
    }
}
