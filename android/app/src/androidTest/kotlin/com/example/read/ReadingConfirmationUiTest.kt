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

class ReadingConfirmationUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun onlyExplicitPassageAndSentenceReportsAdvancePageProgressAndMarker() {
        val catalog = ArchitectureSources.catalog(); val native = CommandExecutor(BuiltInComponents.registry(), catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(native, Neet2026Catalog(), library, catalog)
        val state = mutableStateOf(ArchitectureActivities.initial())
        fun data() = state.value.activities.getValue("reader").data as ActivityData.Reader
        fun act(command: AppCommand) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "reading-touch", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) { Column(Modifier.fillMaxSize()) {
            TextButton({ act(AppCommand.SetPreferences(state.value.preferences.copy(readingSizeSp = 36))) }) { Text("Fixture larger text") }
            ReaderRenderer().Render(ComponentRenderContext(state.value.components.getValue("reader-view"), state.value.activities.getValue("reader"), catalog,
                object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                { commands -> commands.forEach { act(AppCommand.WorkspaceAction(it)) } }, { act(LearningCommandBridge.command(it)) }))
        } } }
        val sentences = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).filter { it.source.elementId == "cell-p1" }
        fun tap(index: Int) {
            val passage = compose.onNodeWithTag("passage-cell-p1").performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            passage.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            passage.performTouchInput { click(layouts.single().getBoundingBox(sentences[index].start + 2).center) }
        }
        tap(0)
        compose.runOnIdle { assertTrue(data().readingConfirmations.isEmpty()) }
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("0 of 2 page passages confirmed · 0 sentence checkpoints")
        compose.onNodeWithTag("reading-confirm-cell-p1-${sentences[0].id}").performScrollTo().performClick()
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("0 of 2 page passages confirmed · 1 sentence checkpoints")
        tap(1)
        compose.onNodeWithTag("reading-confirm-cell-p1-${sentences[1].id}").performScrollTo().performClick()
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("1 of 2 page passages confirmed · 2 sentence checkpoints")
        compose.onNodeWithTag("reading-confirm-cell-p2-passage").performScrollTo().performClick()
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("2 of 2 page passages confirmed · 2 sentence checkpoints")
        compose.onNodeWithTag("reading-marker-cell-p2").assertExists()
        lateinit var id: String
        compose.runOnIdle { id = data().readingConfirmations.values.single { it.source.elementId == "cell-p2" }.id }
        compose.onNodeWithTag("reading-clear-$id").performScrollTo().performClick()
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("1 of 2 page passages confirmed · 2 sentence checkpoints")
        compose.onNodeWithText("Fixture larger text").performClick()
        compose.runOnIdle { assertEquals(2, data().readingConfirmations.size); assertTrue(data().bookmarks.isEmpty() && data().annotations.isEmpty()) }
        compose.onNodeWithTag("reading-progress-reader").assertTextEquals("1 of 2 page passages confirmed · 2 sentence checkpoints")
    }
}
