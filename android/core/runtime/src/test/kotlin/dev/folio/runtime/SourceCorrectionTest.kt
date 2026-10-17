package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.*
import dev.folio.runtime.engineering.*
import dev.folio.runtime.reading.SentenceIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SourceCorrectionTest {
    private var machine = "Source."
    private var checksum = "hash"
    private val raw = object : SourceCatalog by TestFixture.catalog {
        override fun documents() = TestFixture.catalog.documents().map { it.copy(checksum = checksum) }
        override fun page(documentId: String, pageIndex: Int) = TestFixture.catalog.page(documentId, pageIndex)?.let { page ->
            page.copy(elements = page.elements.map { if (it.id == "p1") it.copy(originalText = machine) else it })
        }
    }
    private var state = TestFixture.reader()
    private val catalog = CorrectedSourceCatalog(raw) { state.sourceCorrections }
    private val reducer = SourceCorrectionReducer(catalog)
    private fun proposal(id: String = "one", text: String = "Corrected source.", previous: String? = null) =
        AppCommand.ProposeCorrection(id, TestFixture.source, machine, text, previous)
    private fun apply(command: AppCommand, actor: CommandActor = CommandActor.USER) { state = reducer.apply(state, command, actor) }
    @Test fun proposalStaysUnappliedUntilApprovedAndNeverChangesMachineTextOrReviewStatus() {
        apply(proposal(), CommandActor.AI)
        assertEquals(CorrectionAuthor.AI, state.sourceCorrections.getValue("one").author)
        assertTrue(requireNotNull(catalog.page("doc", 0)).corrections.isEmpty())
        apply(AppCommand.ApproveCorrection("one"))
        val page = requireNotNull(catalog.page("doc", 0))
        assertEquals("Source.", page.elements.first().originalText)
        assertEquals("Corrected source.", page.corrections["p1"])
        assertEquals(EngineeringStatus.UNCHECKED, page.status)
        assertTrue(SentenceIndex().compile(page).first().usesCorrection)
        assertTrue(requireNotNull(raw.page("doc", 0)).corrections.isEmpty())
    }
    @Test fun aiCannotApproveOrRemoveEvenThroughDirectRuntimeCalls() {
        apply(proposal(), CommandActor.AI)
        val before = state
        for (command in listOf(AppCommand.ApproveCorrection("one"), AppCommand.RemoveCorrection("one")))
            assertTrue(runCatching { apply(command, CommandActor.AI) }.isFailure)
        assertEquals(before, state)
    }
    @Test fun supersededApprovalsNeverReactivateWhenCurrentApprovalIsRemoved() {
        apply(proposal()); apply(AppCommand.ApproveCorrection("one"))
        apply(proposal("two", "Second correction.", "one")); apply(AppCommand.ApproveCorrection("two"))
        assertEquals(CorrectionStatus.SUPERSEDED, state.sourceCorrections.getValue("one").status)
        apply(AppCommand.RemoveCorrection("two"))
        assertTrue(requireNotNull(catalog.page("doc", 0)).corrections.isEmpty())
        assertEquals(2, state.sourceCorrections.size)
    }
    @Test fun staleApprovalAndMachineTextCannotBeOverwritten() {
        apply(proposal()); apply(AppCommand.ApproveCorrection("one"))
        apply(proposal("two", "Second correction.", "one"))
        apply(proposal("stale", "Older proposal.", "one"))
        apply(AppCommand.ApproveCorrection("two"))
        assertTrue(runCatching { apply(AppCommand.ApproveCorrection("stale")) }.isFailure)
        machine = "Reprocessed source."
        assertTrue(requireNotNull(catalog.page("doc", 0)).corrections.isEmpty())
        assertTrue(runCatching { apply(AppCommand.ApproveCorrection("stale")) }.isFailure)
        assertEquals(CorrectionStatus.PROPOSED, state.sourceCorrections.getValue("stale").status)
    }
    @Test fun changedChecksumDeactivatesOverlayAndRejectsOldProposal() {
        apply(proposal()); apply(AppCommand.ApproveCorrection("one"))
        apply(proposal("pending", "Pending correction.", "one"))
        checksum = "new-checksum"
        assertTrue(requireNotNull(catalog.page("doc", 0)).corrections.isEmpty())
        assertTrue(runCatching { apply(AppCommand.ApproveCorrection("pending")) }.isFailure)
    }
    @Test fun wrongMachineTextAndFigureTargetsAreRejected() {
        for (command in listOf(proposal().copy(expectedOriginal = "Wrong"), proposal().copy(source = TestFixture.source.copy(elementId = "figure")),
            proposal().copy(source = TestFixture.source.copy(pageIndex = 99))))
            assertTrue(runCatching { apply(command) }.isFailure)
        assertTrue(state.sourceCorrections.isEmpty())
    }
    @Test fun rejectedAiApprovalBatchDoesNotEvenPersistItsProposal() {
        val curriculum = object : CurriculumCatalog {
            override val baseline = "test"; override val sourceUrl = "https://test.example"
            override fun subjects() = emptyList<dev.folio.model.library.Subject>()
            override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
        }
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, catalog)
        val result = executor.execute(state, AppCommandBatch(requestId = "malicious", commands = listOf(proposal(), AppCommand.ApproveCorrection("one"))), TestFixture.ai)
        assertFalse(result.trace.accepted)
        assertEquals(state, result.state)
        val actions = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(state, emptyList())).jsonObject.getValue("actions").jsonArray
        for (id in listOf("source.correction.approve", "source.correction.remove"))
            assertFalse(actions.single { it.jsonObject.getValue("id").jsonPrimitive.content == id }.jsonObject.getValue("enabled").jsonPrimitive.boolean)
    }
    @Test fun strictCorrectionWireRoundTripsWithoutAuthorOrApprovalMetadata() {
        val codec = AppCommandCodec()
        val batch = AppCommandBatch(requestId = "correction", commands = listOf(proposal(), AppCommand.ApproveCorrection("one"), AppCommand.RemoveCorrection("one")))
        assertEquals(batch, codec.decode(codec.encode(batch)))
    }
}
