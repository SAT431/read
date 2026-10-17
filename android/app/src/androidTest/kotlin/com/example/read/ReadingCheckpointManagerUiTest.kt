package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import dev.folio.model.source.SourcePage
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.reader.ReaderRenderer
import dev.folio.ui.component.ComponentRenderContext
import dev.folio.ui.media.LocalSourceRevision
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingCheckpointManagerUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun changedAndMissingSourcesStayInspectableAndClearableAtLargeText() {
        val base = ArchitectureSources.catalog(); val changed = mutableStateOf(false); val missing = mutableStateOf(false)
        val catalog = object : SourceCatalog by base {
            override fun page(documentId: String, pageIndex: Int): SourcePage? = if (missing.value) null else
                base.page(documentId, pageIndex)?.let { if (changed.value) it.copy(corrections = mapOf("cell-p1" to "Changed fixture wording.")) else it }
        }
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(CommandExecutor(BuiltInComponents.registry(), catalog), Neet2026Catalog(), library, catalog)
        val state = mutableStateOf(ArchitectureActivities.initial())
        fun data() = state.value.activities.getValue("reader").data as ActivityData.Reader
        fun act(vararg commands: AppCommand) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "manager-touch", commands = commands.toList()), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        val sentences = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0)))
        val first = sentences.first { it.source.elementId == "cell-p1" }; val other = sentences.first { it.source.elementId == "cell-p2" }
        act(AppCommand.ConfirmReading("reader", first.source, first.id, 0), AppCommand.ConfirmReading("reader", other.source, expectedReadingRevision = 1))
        val stale = data().readingConfirmations.values.single { it.source == first.source }
        val valid = data().readingConfirmations.values.single { it.source == other.source }
        changed.value = true
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, 1.8f), LocalSourceRevision provides (if (missing.value) 2L else 1L)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TextButton({ missing.value = true }) { Text("Fixture remove page reconstruction") }
                ReaderRenderer().Render(ComponentRenderContext(state.value.components.getValue("reader-view"), state.value.activities.getValue("reader"), catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences,
                    { commands -> act(*commands.map(AppCommand::WorkspaceAction).toTypedArray()) }, { act(LearningCommandBridge.command(it)) }))
            }
        } } }
        compose.onNodeWithTag("reading-checkpoints-show-reader").performScrollTo().performClick()
        compose.onNodeWithTag("reading-checkpoint-open-${stale.id}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("reading-checkpoint-unavailable-${stale.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reading-checkpoint-open-${valid.id}").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(other.source, state.value.context.source)
            assertEquals(valid, data().readingConfirmations.getValue(valid.id)); assertEquals(2L, data().readingRevision)
        }
        compose.onNodeWithText("Fixture remove page reconstruction").performScrollTo().performClick()
        compose.onNodeWithTag("reading-checkpoint-open-${valid.id}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("reading-checkpoint-clear-${stale.id}").performScrollTo().assertIsDisplayed()
        compose.waitForIdle(); saveFixtureScreenshot("reading-checkpoint-manager.png")
        compose.onNodeWithTag("reading-checkpoint-clear-${stale.id}").performClick()
        compose.runOnIdle {
            assertFalse(data().readingConfirmations.containsKey(stale.id))
            assertEquals(valid, data().readingConfirmations.getValue(valid.id))
            assertTrue(data().readingCheckpointsVisible); assertTrue(data().bookmarks.isEmpty())
        }
    }
}
