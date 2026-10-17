package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.LearningReducer
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.reader.ReaderRenderer
import dev.folio.ui.component.ComponentRenderContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderSourceFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun sourceReferenceBringsOffscreenPassageIntoViewAndClearsOldSentence() {
        val page = SourcePage("reader-link-fixture", 0, "Linked source fixture", (0..9).map {
            SourceElement("p$it", SourceElementKind.PARAGRAPH, "Passage number $it describes a sample source concept. Its wording stays intact.", it)
        }, isFixture = true)
        val catalog = InMemorySourceCatalog(listOf(SourceDocument(page.documentId, page.title, 1, "fixture", null)), emptyList(), listOf(page))
        val first = SourceAnchor(page.documentId, 0, "p0")
        val activity = Activity("reader", first, ActivityData.Reader(selectedSentence = SentenceIndex().compile(page).first().id))
        val component = ComponentInstance("reader-view", activity.id, ComponentKind.READER)
        val state = mutableStateOf(Workspace(activities = mapOf(activity.id to activity), components = mapOf(component.id to component),
            context = StudyContext(source = first, focusedActivityId = activity.id)))
        val executor = CommandExecutor(BuiltInComponents.registry(), catalog)
        val reducer = LearningReducer(catalog)
        fun commands(list: List<WorkspaceCommand>) {
            val result = executor.execute(state.value, CommandBatch(requestId = "source", commands = list), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxSize()) {
                TextButton({ commands(listOf(WorkspaceCommand.FocusSource(first.copy(elementId = "p9")))) }) { Text("Open linked passage") }
                ReaderRenderer().Render(ComponentRenderContext(component, state.value.activities.getValue(activity.id), catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null },
                    state.value.preferences, ::commands, { state.value = reducer.reduce(state.value, it) }))
            }
        } }
        compose.onNodeWithTag("passage-p0").assertIsDisplayed()
        compose.onNodeWithText("Open linked passage").performClick()
        compose.onNodeWithTag("passage-p9").assertIsDisplayed()
        compose.onNodeWithText("Passage selected").assertIsDisplayed()
        compose.runOnIdle {
            assertNull((state.value.activities.getValue(activity.id).data as ActivityData.Reader).selectedSentence)
            assertEquals("p9", state.value.context.source?.elementId)
        }
    }
}
