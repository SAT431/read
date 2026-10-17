package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.reader.ReaderRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingBookmarkUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun sentenceBookmarkReopensExactTargetAcrossPagesAndCanBeRemoved() {
        val catalog = ArchitectureSources.catalog()
        val native = CommandExecutor(BuiltInComponents.registry(), catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val app = AppCommandExecutor(native, Neet2026Catalog(), library, catalog)
        val state = mutableStateOf(ArchitectureActivities.initial())
        fun data() = state.value.activities.getValue("reader").data as ActivityData.Reader
        fun act(command: AppCommand) {
            val result = app.execute(state.value, AppCommandBatch(requestId = "bookmark-touch", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first { it.source.elementId == "cell-p1" }
        compose.setContent { FolioTheme(false) { Column(Modifier.fillMaxSize()) {
            TextButton({ act(AppCommand.WorkspaceAction(WorkspaceCommand.OpenPage(ArchitectureSources.DOCUMENT_ID, 1))) }) { Text("Fixture next page") }
            ReaderRenderer().Render(ComponentRenderContext(state.value.components.getValue("reader-view"), state.value.activities.getValue("reader"), catalog,
                object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                { commands -> commands.forEach { act(AppCommand.WorkspaceAction(it)) } }, { act(LearningCommandBridge.command(it)) }))
        } } }
        val passage = compose.onNodeWithTag("passage-cell-p1").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        passage.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        passage.performTouchInput { click(layouts.single().getBoundingBox(sentence.start + 2).center) }
        compose.onNodeWithTag("reader-bookmark-cell-p1-${sentence.id}").performScrollTo().performClick()
        lateinit var id: String
        compose.runOnIdle {
            val item = data().bookmarks.values.single(); id = item.id
            assertEquals(sentence.id, item.sentenceId); assertFalse(item.byAssistant)
        }
        compose.onNodeWithText("Fixture next page").performClick()
        compose.runOnIdle { assertEquals(1, state.value.activities.getValue("reader").source.pageIndex) }
        compose.onNodeWithTag("reader-bookmarks-show-reader").performClick()
        compose.onNodeWithTag("reader-bookmark-open-$id").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(sentence.source, state.value.context.source)
            assertEquals(sentence.id, data().selectedSentence)
            assertEquals(1, data().bookmarkJump)
        }
        compose.onNodeWithTag("passage-cell-p1").assertExists()
        compose.onNodeWithTag("reader-bookmark-remove-$id").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(data().bookmarks.isEmpty()) }
        compose.onNodeWithText("No reading bookmarks yet.").assertExists()
    }
}
