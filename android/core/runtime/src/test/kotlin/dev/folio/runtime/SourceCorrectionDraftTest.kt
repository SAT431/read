package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.engineering.*
import dev.folio.runtime.interaction.InteractionGate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SourceCorrectionDraftTest {
    private val draft = SourceCorrectionDraft(TestFixture.source,
        TestFixture.catalog.documents().first().checksum, "Source.", "Private unfinished correction")
    private val reducer = SourceCorrectionDraftReducer(TestFixture.catalog)
    private fun save(state: Workspace = Workspace(), value: SourceCorrectionDraft? = draft, id: String = "draft") =
        reducer.apply(state, AppCommand.SaveCorrectionDraft(id, value), CommandActor.USER)
    @Test fun editingRetainsCapturedIdentityEvenWhenSourceChangesAndRejectsRebinding() {
        val first = save()
        assertEquals(draft, first.correctionDrafts["draft"])
        assertTrue(runCatching { save(first, draft.copy(originalText = "Different source")) }.isFailure)
        val changed = save(first, draft.copy(replacement = ""))
        assertEquals("", changed.correctionDrafts.getValue("draft").replacement)
        assertTrue(changed.sourceCorrections.isEmpty())
        assertTrue(save(changed, null).correctionDrafts.isEmpty())
    }
    @Test fun duplicateAnchorsFiguresUnknownSourcesAndWrongChecksumAreRejected() {
        for (invalid in listOf(draft.copy(source = draft.source.copy(elementId = "figure")),
            draft.copy(source = draft.source.copy(pageIndex = 99)), draft.copy(documentChecksum = "wrong")))
            assertTrue(runCatching { save(value = invalid) }.isFailure)
        assertTrue(runCatching { save(save(), id = "other") }.isFailure)
    }
    @Test fun reprocessedSourceDoesNotPreventRecoveringEditingOrDiscardingExistingDraft() {
        val before = save()
        val changed = object : SourceCatalog by TestFixture.catalog {
            override fun documents() = TestFixture.catalog.documents().map { it.copy(checksum = "new-checksum") }
        }
        val reducer = SourceCorrectionDraftReducer(changed)
        val edited = reducer.apply(before, AppCommand.SaveCorrectionDraft("draft", draft.copy(replacement = "Retained edit")), CommandActor.USER)
        assertEquals("Retained edit", edited.correctionDrafts.getValue("draft").replacement)
        assertTrue(reducer.apply(edited, AppCommand.SaveCorrectionDraft("draft", null), CommandActor.USER).correctionDrafts.isEmpty())
        assertTrue(runCatching { reducer.apply(Workspace(), AppCommand.SaveCorrectionDraft("new", draft), CommandActor.USER) }.isFailure)
    }
    @Test fun aiCannotReadDraftsInContextOrWriteOrDiscardThem() {
        val curriculum = object : CurriculumCatalog {
            override val baseline = "test"; override val sourceUrl = "https://test.example"
            override fun subjects() = emptyList<dev.folio.model.library.Subject>()
            override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
        }
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val before = save()
        val context = AppCapabilityContext(curriculum).encode(before, emptyList())
        assertFalse(context.contains(draft.replacement))
        assertFalse(context.contains("correctionDrafts"))
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
        for (value in listOf(draft.copy(replacement = "AI replacement"), null)) {
            val result = executor.execute(before, AppCommandBatch(requestId = "ai", commands = listOf(
                AppCommand.SaveCorrectionDraft("draft", value))), TestFixture.ai)
            assertFalse(result.trace.accepted); assertEquals(before, result.state)
        }
    }
    @Test fun onlyMatchingSuccessfulUserProposalClearsDraftAndFailurePreservesIt() {
        val before = save()
        val corrections = SourceCorrectionReducer(TestFixture.catalog)
        val proposal = AppCommand.ProposeCorrection("p", draft.source, draft.originalText, draft.replacement)
        assertTrue(runCatching { corrections.apply(before, proposal.copy(expectedOriginal = "Wrong"), CommandActor.USER) }.isFailure)
        assertEquals(draft, before.correctionDrafts["draft"])
        assertEquals(before.correctionDrafts, corrections.apply(before, proposal, CommandActor.AI).correctionDrafts)
        assertEquals(before.correctionDrafts, corrections.apply(before, proposal.copy(replacement = "Other wording"), CommandActor.USER).correctionDrafts)
        assertTrue(corrections.apply(before, proposal, CommandActor.USER).correctionDrafts.isEmpty())
    }
    @Test fun restoredDraftProtectsOnlyItsVisibleComparisonPageBeforeComposition() {
        val gate = InteractionGate()
        val state = save().copy(navigation = AppNavigation(AppDestination.PDF, pdfId = "pdf", sourceReviewVisible = true))
        assertFalse(gate.locked)
        assertTrue(gate.blocks(state, "doc"))
        assertFalse(gate.blocks(state, "other"))
        assertFalse(gate.blocks(state.copy(navigation = state.navigation.copy(pdfPageIndex = 1)), "doc"))
        assertFalse(gate.blocks(state.copy(navigation = state.navigation.copy(sourceReviewVisible = false)), "doc"))
        assertFalse(gate.blocks(state.copy(navigation = AppNavigation()), "doc"))
    }
}
