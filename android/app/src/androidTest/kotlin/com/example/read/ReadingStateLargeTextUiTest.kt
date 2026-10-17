package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

class ReadingStateLargeTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun savedTargetsAndConfirmationControlsRemainReachableWithLargeText() {
        val catalog = ArchitectureSources.catalog(); val native = CommandExecutor(BuiltInComponents.registry(), catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(native, Neet2026Catalog(), library, catalog)
        val state = mutableStateOf(ArchitectureActivities.initial())
        fun act(vararg commands: AppCommand) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "large-reading", commands = commands.toList()), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        fun data() = state.value.activities.getValue("reader").data as ActivityData.Reader
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first()
        act(AppCommand.AddReadingBookmark("reader", "large-bookmark", sentence.source, sentence.id),
            AppCommand.ConfirmReading("reader", sentence.source, expectedReadingRevision = 0),
            AppCommand.ShowReadingBookmarks("reader", true), AppCommand.FocusSentence("reader", sentence.id))
        val confirmationId = data().readingConfirmations.keys.single()
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("fixture-reading-root")) {
                ReaderRenderer().Render(ComponentRenderContext(state.value.components.getValue("reader-view"), state.value.activities.getValue("reader"), catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                    { commands -> act(*commands.map(AppCommand::WorkspaceAction).toTypedArray()) }, { act(LearningCommandBridge.command(it)) }))
            }
        } } }
        compose.onNodeWithTag("reader-bookmark-open-large-bookmark").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(sentence.id, data().selectedSentence) }
        compose.onNodeWithTag("reading-clear-$confirmationId").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("reading-marker-cell-p1").assertExists()
        compose.onNodeWithTag("fixture-reading-root").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 250f) }
        compose.onNodeWithTag("reading-clear-$confirmationId").assertIsDisplayed()
        compose.waitForIdle(); saveFixtureScreenshot("reader-state-large-text.png")
        compose.onNodeWithTag("reading-clear-$confirmationId").performClick()
        compose.onNodeWithTag("reading-progress-reader").performScrollTo().assertTextEquals("0 of 2 page passages confirmed · 0 sentence checkpoints")
        compose.onNodeWithTag("reader-bookmark-remove-large-bookmark").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(data().bookmarks.isEmpty() && data().readingConfirmations.isEmpty()) }
    }
}
