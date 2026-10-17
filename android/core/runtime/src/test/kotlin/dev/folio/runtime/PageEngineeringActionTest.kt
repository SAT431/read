package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PageEngineeringActionTest {
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "chapter",
            TestFixture.catalog.documents().first().copy(checksum = "a".repeat(64), localPdfPath = "/private/original.pdf"), 0)))
        override suspend fun load() {}
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://test.example"
        override fun subjects() = emptyList<Subject>()
        override fun chapters() = emptyList<StudyChapter>()
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library)
    @Test fun aiAndTouchRequestTheSameValidatedBackgroundEffect() {
        val batch = AppCommandBatch(requestId = "process", expectedRevision = 0,
            commands = listOf(AppCommand.EngineerPage("doc", 2)))
        for (policy in listOf(TestFixture.user, TestFixture.ai)) {
            val result = executor.execute(TestFixture.reader(), batch, policy)
            assertTrue(result.trace.accepted)
            val effect = result.effects.single() as AppEffect.EngineerPage
            assertEquals(2, effect.request.pageIndex); assertEquals("a".repeat(64), effect.request.documentChecksum)
            assertEquals(TestFixture.reader().context, result.state.context)
        }
    }
    @Test fun invalidLaterPageRejectsAllQueuedEffectsAndNavigation() {
        val before = TestFixture.reader()
        val result = executor.execute(before, AppCommandBatch(requestId = "bad", commands = listOf(
            AppCommand.Navigate(AppDestination.SETTINGS), AppCommand.EngineerPage("doc", 0), AppCommand.EngineerPage("doc", 99))), TestFixture.ai)
        assertFalse(result.trace.accepted); assertTrue(result.effects.isEmpty()); assertEquals(before, result.state)
    }
}
