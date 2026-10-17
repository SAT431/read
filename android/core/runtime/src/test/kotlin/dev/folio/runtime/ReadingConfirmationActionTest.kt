package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.runtime.action.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReadingConfirmationActionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<Subject>()
        override fun chapters() = emptyList<StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>())
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    @Test fun invalidSecondCommandRejectsWholeBatchAndWireRoundTripsNativeReportActions() {
        val confirm = AppCommand.ConfirmReading("read", TestFixture.source, expectedReadingRevision = 0)
        val clear = AppCommand.ClearReadingConfirmation("read", "unknown", 999)
        val initial = TestFixture.reader()
        val failed = executor.execute(initial, AppCommandBatch(requestId = "reading", commands = listOf(confirm, clear)), TestFixture.ai)
        assertFalse(failed.trace.accepted); assertEquals(initial, failed.state)
        val json = Json { encodeDefaults = true }
        for (action in listOf<AppCommand>(confirm, clear)) {
            assertEquals(action, json.decodeFromString<AppCommand>(json.encodeToString(action)))
            assertEquals(action, LearningCommandBridge.command(requireNotNull(LearningCommandBridge.event(action))))
        }
    }
    @Test fun providerReceivesCurrentPageProgressAndReportActorWithoutAnInferredMasteryField() {
        val saved = executor.execute(TestFixture.reader(), AppCommandBatch(requestId = "reading", commands = listOf(
            AppCommand.ConfirmReading("read", TestFixture.source, expectedReadingRevision = 0))), TestFixture.ai)
        assertTrue(saved.trace.error, saved.trace.accepted)
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum, sourceCatalog = TestFixture.catalog).encode(saved.state, emptyList())).jsonObject
        val progress = context.getValue("readingProgress").jsonObject.getValue("read").jsonObject
        assertEquals(1, progress.getValue("confirmedPassages").jsonPrimitive.int)
        assertEquals(1, progress.getValue("passages").jsonPrimitive.int)
        val reader = saved.state.activities.getValue("read").data as ActivityData.Reader
        assertTrue(reader.readingConfirmations.values.single().byAssistant)
        assertFalse(progress.containsKey("mastery") || progress.containsKey("comprehension") || progress.containsKey("gaze"))
        assertTrue(context.getValue("actions").jsonArray.any { it.jsonObject.getValue("id").jsonPrimitive.content == "reader.confirm_read" })
    }

    @Test fun checkpointCommandsAreAtomicAndOpeningSynchronizesPdfContextWithoutReconfirming() {
        library.entries.value = listOf(LibraryPdf("saved-pdf", "unlisted-unit", TestFixture.catalog.documents().single(), 0))
        val initial = TestFixture.reader()
        val saved = executor.execute(initial, AppCommandBatch(requestId = "save", commands = listOf(
            AppCommand.ConfirmReading("read", TestFixture.source, expectedReadingRevision = 0))), TestFixture.user).state
        val report = (saved.activities.getValue("read").data as ActivityData.Reader).readingConfirmations.values.single()
        val commands = listOf(AppCommand.ShowReadingCheckpoints("read", true), AppCommand.OpenReadingConfirmation("read", report.id, report.version))
        val result = executor.execute(saved, AppCommandBatch(requestId = "open", commands = commands), TestFixture.ai)
        assertTrue(result.trace.error, result.trace.accepted); assertEquals("saved-pdf", result.state.navigation.pdfId)
        assertEquals(0, result.state.navigation.pdfPageIndex)
        assertEquals(report, (result.state.activities.getValue("read").data as ActivityData.Reader).readingConfirmations.getValue(report.id))
        val failed = executor.execute(saved, AppCommandBatch(requestId = "stale", commands = commands.dropLast(1) +
            AppCommand.OpenReadingConfirmation("read", report.id, report.version + 1)), TestFixture.ai)
        assertFalse(failed.trace.accepted); assertEquals(saved, failed.state)
        val json = Json { encodeDefaults = true }
        for (command in commands) {
            assertEquals(command, json.decodeFromString<AppCommand>(json.encodeToString(command)))
            assertEquals(command, LearningCommandBridge.command(requireNotNull(LearningCommandBridge.event(command))))
        }
    }
}
