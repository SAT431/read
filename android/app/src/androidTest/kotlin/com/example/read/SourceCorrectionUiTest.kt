package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.local.snapshot.WorkspaceSnapshotCodec
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.engineering.CorrectedSourceCatalog
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.reader.ReadingCompiler
import dev.folio.study.review.SourceReviewScreen
import dev.folio.ui.interaction.LocalInteractionGate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceCorrectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun proposalNeedsApprovalAndRemovalRestoresMachineReadingAfterRecovery() {
        val original = SourcePage("correction-fixture", 0, "Correction sample", listOf(
            SourceElement("p0", SourceElementKind.PARAGRAPH, "Machine wording.", 0)))
        val document = SourceDocument(original.documentId, "Original sample", 1, "sample-checksum", null)
        val raw = InMemorySourceCatalog(listOf(document), emptyList(), listOf(original))
        val state = mutableStateOf(Workspace(navigation = AppNavigation(AppDestination.PDF, pdfId = "pdf", sourceReviewVisible = true)))
        val catalog = CorrectedSourceCatalog(raw) { state.value.sourceCorrections }
        val curriculum = Neet2026Catalog()
        val pdf = LibraryPdf("pdf", curriculum.chapters().first().id, document, 0)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(listOf(pdf))
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(CommandExecutor(BuiltInComponents.registry(), catalog), curriculum, library, catalog)
        val gate = InteractionGate()
        val visible = mutableStateOf(true)
        fun dispatch(command: AppCommand) {
            val result = executor.execute(state.value, AppCommandBatch(requestId = "touch", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
        }
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalInteractionGate provides gate) {
            Column(Modifier.fillMaxSize()) {
                TextButton({ state.value = WorkspaceSnapshotCodec().decode(WorkspaceSnapshotCodec().encode(state.value)) }) { Text("Recover correction snapshot") }
                TextButton({ visible.value = !visible.value }) { Text(if (visible.value) "Leave comparison" else "Return to comparison") }
                if (visible.value) SourceReviewScreen(pdf, state.value.navigation, catalog.page(document.id, 0), null,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, ::dispatch,
                    state.value.sourceCorrections, state.value.correctionDrafts) {
                    Text("Original comparison fixture")
                }
            }
        } } }
        val list = compose.onNodeWithTag("reconstruction-review")
        list.performScrollToNode(hasText("Suggest correction"))
        compose.onNodeWithText("Suggest correction").performClick()
        compose.onNodeWithTag("correction-draft-p0").performTextReplacement("Learner-corrected wording.")
        compose.runOnIdle { assertTrue(gate.locked) }
        compose.onNodeWithText("Leave comparison").performClick()
        compose.runOnIdle { assertFalse(gate.locked); assertTrue(state.value.sourceCorrections.isEmpty()) }
        compose.onNodeWithText("Recover correction snapshot").performClick()
        compose.onNodeWithText("Return to comparison").performClick()
        list.performScrollToNode(hasTestTag("correction-draft-p0"))
        compose.onNodeWithTag("correction-draft-p0").assertTextContains("Learner-corrected wording.")
        compose.runOnIdle { assertTrue(gate.locked) }
        list.performScrollToNode(hasText("Save proposal"))
        compose.onNodeWithText("Save proposal").performClick()
        compose.runOnIdle {
            assertFalse(gate.locked)
            assertTrue(state.value.correctionDrafts.isEmpty())
            assertTrue(requireNotNull(catalog.page(document.id, 0)).corrections.isEmpty())
            assertEquals("Machine wording.", ReadingCompiler().compile(requireNotNull(catalog.page(document.id, 0))).single().displayedText)
        }
        list.performScrollToNode(hasText("Approve correction"))
        compose.onNodeWithText("Approve correction").performClick()
        compose.onNodeWithText("Recover correction snapshot").performClick()
        compose.runOnIdle {
            val page = requireNotNull(catalog.page(document.id, 0))
            assertEquals("Learner-corrected wording.", ReadingCompiler().compile(page).single().displayedText)
            assertEquals("Machine wording.", page.elements.single().originalText)
            assertEquals(EngineeringStatus.UNCHECKED, page.status)
        }
        list.performScrollToNode(hasText("Remove approval"))
        compose.onNodeWithText("Remove approval").performClick()
        compose.runOnIdle {
            assertEquals("Machine wording.", ReadingCompiler().compile(requireNotNull(catalog.page(document.id, 0))).single().displayedText)
            assertEquals(CorrectionStatus.REMOVED, state.value.sourceCorrections.values.single().status)
            assertEquals(original, raw.page(document.id, 0))
        }
        list.performScrollToNode(hasText("Suggest correction"))
        compose.onNodeWithText("Suggest correction").performClick()
        compose.onNodeWithTag("correction-draft-p0").performTextReplacement("Discard this unfinished correction")
        list.performScrollToNode(hasText("Discard correction draft"))
        compose.onNodeWithText("Discard correction draft").performClick()
        compose.runOnIdle { assertTrue(state.value.correctionDrafts.isEmpty()); assertFalse(gate.locked) }
    }
}
