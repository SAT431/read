package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSourceSelection
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SourceReviewRegionTest {
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun page(documentId: String, pageIndex: Int) = TestFixture.catalog.page(documentId, pageIndex)?.let { page -> page.copy(
            widthPoints = 200f, heightPoints = 100f, elements = page.elements.map {
                if (it.id == "p1") it.copy(bounds = SourceBounds(10f, 10f, 80f, 40f)) else it
            }) }
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 3, 1))
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "cell", catalog.documents().first(), 0)))
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, catalog)
    private fun execute(state: Workspace = TestFixture.reader(), vararg commands: AppCommand, policy: ExecutionPolicy = TestFixture.user) =
        executor.execute(state, AppCommandBatch(requestId = "region", commands = commands.toList()), policy)
    @Test fun aiAndTouchOpenSameOriginalRegionWithoutChangingLearningOrSource() {
        val before = TestFixture.reader()
        val command = AppCommand.ReviewRegion(TestFixture.source)
        val touch = execute(before, command)
        val ai = execute(before, command, policy = TestFixture.ai)
        assertTrue(touch.trace.accepted); assertEquals(touch.state, ai.state)
        assertEquals("p1", touch.state.navigation.reviewElementId)
        assertTrue(touch.state.navigation.sourceReviewVisible)
        assertEquals(before.activities, touch.state.activities)
        assertEquals(before.context, touch.state.context)
        assertTrue(touch.state.sourceCorrections.isEmpty())
    }
    @Test fun pageChangesClearSelectionAndClearRegionRetainsViewTransform() {
        val selected = execute(commands = arrayOf(AppCommand.ReviewRegion(TestFixture.source), AppCommand.PdfTransform(2f, 20f, 30f))).state
        val cleared = execute(selected, AppCommand.ReviewRegion(null))
        assertTrue(cleared.trace.accepted); assertNull(cleared.state.navigation.reviewElementId)
        assertEquals(2f, cleared.state.navigation.pdfZoom)
        val next = execute(selected, AppCommand.NextPdfPage)
        assertTrue(next.trace.accepted); assertNull(next.state.navigation.reviewElementId)
        assertEquals(1, next.state.navigation.pdfPageIndex)
        val reopened = execute(selected, AppCommand.ReviewRegion(TestFixture.source))
        assertEquals(1f, reopened.state.navigation.pdfZoom)
        assertEquals(0f, reopened.state.navigation.pdfPanX)
    }
    @Test fun unknownUnboundedAndUnimportedSourcesRejectAtomically() {
        for (source in listOf(TestFixture.source.copy(elementId = "figure"), TestFixture.source.copy(documentId = "missing"),
            TestFixture.source.copy(pageIndex = 99), TestFixture.source.copy(elementId = null))) {
            val result = execute(commands = arrayOf(AppCommand.ReviewRegion(TestFixture.source), AppCommand.ReviewRegion(source)))
            assertFalse(result.trace.accepted); assertEquals(TestFixture.reader(), result.state)
        }
        assertFalse(execute(commands = arrayOf(AppCommand.ReviewRegion(null))).trace.accepted)
        library.entries.value = emptyList()
        assertFalse(execute(commands = arrayOf(AppCommand.ReviewRegion(TestFixture.source))).trace.accepted)
    }
    @Test fun strictWireIncludesSelectionAndClearWithoutExecutableGeometry() {
        val codec = AppCommandCodec()
        val batch = AppCommandBatch(requestId = "region", commands = listOf(AppCommand.ReviewRegion(TestFixture.source), AppCommand.ReviewRegion(null)))
        assertEquals(batch, codec.decode(codec.encode(batch)))
    }
    @Test fun assistantUsesHighlightedElementOnlyWhileItsCurrentRegionIsAvailable() {
        val selected = execute(commands = arrayOf(AppCommand.ReviewRegion(TestFixture.source))).state
        assertEquals(TestFixture.source, AssistantSourceSelection.resolve(selected, library.entries.value, catalog))
        val assistant = selected.copy(navigation = selected.navigation.copy(destination = AppDestination.ASSISTANT, assistantOrigin = AppDestination.PDF))
        assertEquals(TestFixture.source, AssistantSourceSelection.resolve(assistant, library.entries.value, catalog))
        assertEquals(TestFixture.source.copy(elementId = null), AssistantSourceSelection.resolve(
            selected.copy(navigation = selected.navigation.copy(reviewElementId = "missing")), library.entries.value, catalog))
        assertEquals(TestFixture.source.copy(elementId = null), AssistantSourceSelection.resolve(
            selected.copy(navigation = selected.navigation.copy(sourceReviewVisible = false)), library.entries.value, catalog))
    }
}
