package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class AppActionTest {
    @Test fun optionalSourceReviewNavigatesPagesAndBackReturnsToOriginalWithoutApproval() {
        val reviewed = execute(AppCommand.ReviewPage("doc", 1), AppCommand.NextPdfPage)
        assertTrue(reviewed.trace.accepted)
        assertTrue(reviewed.state.navigation.sourceReviewVisible)
        assertEquals(2, reviewed.state.navigation.pdfPageIndex)
        assertEquals(TestFixture.reader().activities, reviewed.state.activities)
        val closed = executor.execute(reviewed.state, AppCommandBatch(requestId = "close", commands = listOf(AppCommand.Back)), TestFixture.user)
        assertEquals(AppDestination.PDF, closed.state.navigation.destination)
        assertEquals(2, closed.state.navigation.pdfPageIndex)
        assertFalse(closed.state.navigation.sourceReviewVisible)
    }
    @Test fun invalidSourceReviewCannotPartiallyChangeStateAndWireRoundTrips() {
        val codec = AppCommandCodec()
        val batch = AppCommandBatch(requestId = "review", commands = listOf(AppCommand.ReviewPage("doc", 1)))
        assertEquals(batch, codec.decode(codec.encode(batch)))
        val invalid = execute(AppCommand.ReviewPage("doc", 0), AppCommand.ReviewPage("doc", 99))
        assertFalse(invalid.trace.accepted)
        assertEquals(TestFixture.reader(), invalid.state)
        assertFalse(execute(AppCommand.ReviewPage("unknown", 0)).trace.accepted)
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "Test baseline"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 3, 1))
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf1", "cell", SourceDocument("doc", "Cells.pdf", 4, "hash", null), 1)))
        override suspend fun load() = Unit
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library)
    private fun execute(vararg commands: AppCommand) = executor.execute(TestFixture.reader(),
        AppCommandBatch(requestId = "app", commands = commands.toList()), TestFixture.user)

    @Test fun subjectChapterAndPdfNavigationUseSameSemanticDispatcher() {
        val result = execute(AppCommand.OpenSubject(SubjectId.BOTANY), AppCommand.OpenChapterLibrary("cell"), AppCommand.OpenPdf("pdf1"), AppCommand.NextPdfPage)
        assertTrue(result.trace.accepted)
        assertEquals(AppDestination.PDF, result.state.navigation.destination)
        assertEquals(1, result.state.navigation.pdfPageIndex)
    }
    @Test fun invalidSecondActionRejectsStateAndPlatformEffects() {
        val result = execute(AppCommand.RequestPdfImport("cell"), AppCommand.OpenChapterLibrary("missing"))
        assertFalse(result.trace.accepted); assertTrue(result.effects.isEmpty())
        assertEquals(AppDestination.HOME, result.state.navigation.destination)
    }
    @Test fun pickerRequestHasExplicitUserOwnedPlatformEffect() {
        val result = execute(AppCommand.RequestPdfImport("cell"))
        assertTrue(result.trace.accepted)
        assertEquals(AppEffect.RequestPdfImport("cell"), result.effects.single())
    }
    @Test fun pdfBoundariesAndUnsafeZoomAreRejected() {
        assertFalse(execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfPage(10)).trace.accepted)
        assertFalse(execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfZoom(Float.NaN)).trace.accepted)
        assertFalse(execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfTransform(1f, 99999f, 0f)).trace.accepted)
    }
    @Test fun pdfTransformAndPreferencesAreAddressableWithoutUiCoordinates() {
        val result = execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfTransform(2f, 30f, 20f),
            AppCommand.SetPreferences(UserPreferences(darkTheme = true, reducedMotion = true)))
        assertTrue(result.trace.accepted); assertEquals(2f, result.state.navigation.pdfZoom)
        assertTrue(result.state.preferences.darkTheme && result.state.preferences.reducedMotion)
    }
    @Test fun touchAndAiCanUseIdenticalWireCommandsWithoutCode() {
        val codec = AppCommandCodec()
        val batch = AppCommandBatch(requestId = "api", commands = listOf(AppCommand.OpenSubject(SubjectId.BOTANY)))
        assertEquals(batch, codec.decode(codec.encode(batch)))
        val result = executor.execute(TestFixture.reader(), codec.decode(codec.encode(batch)), TestFixture.ai)
        assertTrue(result.trace.accepted)
        assertTrue(runCatching { codec.decode("""{"requestId":"x","commands":[{"type":"subject.open","subject":"BOTANY","script":"bad"}]}""") }.isFailure)
    }
    @Test fun unavailableSentenceCapabilityIsNotFaked() {
        assertFalse(execute(AppCommand.FocusSentence("read", "unknown")).trace.accepted)
    }
    @Test fun capabilityPackageContainsRealIdsAndAvailability() {
        val context = AppCapabilityContext(curriculum).encode(TestFixture.reader(), library.entries.value)
        assertTrue(context.contains("chapter.open") && context.contains("pdf1") && context.contains("cell"))
        val packageData = kotlinx.serialization.json.Json.parseToJsonElement(context) as kotlinx.serialization.json.JsonObject
        assertEquals("1", packageData.getValue("schemaVersion").toString())
        assertTrue(packageData.containsKey("activities") && packageData.containsKey("components"))
        assertTrue(packageData.getValue("actions").toString().contains("\"enabled\":false"))
        assertEquals(AppActionRegistry.all.size, AppActionRegistry.all.map { it.id }.distinct().size)
    }
    @Test fun reopeningPdfFromAssistantRetainsOriginalViewerPosition() {
        val result = execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfPage(2),
            AppCommand.Navigate(AppDestination.ASSISTANT), AppCommand.OpenPdf("pdf1"))
        assertTrue(result.trace.accepted)
        assertEquals(2, result.state.navigation.pdfPageIndex)
        assertEquals(AppDestination.PDF, result.state.navigation.destination)
    }
    @Test fun aiNextPageFromAssistantDisplaysOriginalPdf() {
        val before = execute(AppCommand.OpenPdf("pdf1"), AppCommand.PdfPage(1), AppCommand.Navigate(AppDestination.ASSISTANT)).state
        val result = executor.execute(before, AppCommandBatch(requestId = "next", expectedRevision = before.revision,
            commands = listOf(AppCommand.NextPdfPage)), TestFixture.ai)
        assertTrue(result.trace.accepted)
        assertEquals(2, result.state.navigation.pdfPageIndex)
        assertEquals(AppDestination.PDF, result.state.navigation.destination)
    }
    @Test fun voiceCommandsRoundTripAndEmitValidatedEffectsForAi() {
        val codec = AppCommandCodec()
        val before = TestFixture.reader()
        val batch = AppCommandBatch(requestId = "voice", expectedRevision = before.revision,
            commands = listOf(AppCommand.StartVoice, AppCommand.FinishVoice, AppCommand.CancelVoice))
        val result = executor.execute(before, codec.decode(codec.encode(batch)), TestFixture.ai)
        assertTrue(result.trace.accepted)
        assertEquals(listOf(AppEffect.StartVoice, AppEffect.FinishVoice, AppEffect.CancelVoice), result.effects)
    }
    @Test fun invalidOrStaleBatchCannotStartMicrophone() {
        val before = TestFixture.reader()
        val invalid = execute(AppCommand.StartVoice, AppCommand.OpenChapterLibrary("missing"))
        assertFalse(invalid.trace.accepted)
        assertTrue(invalid.effects.isEmpty())
        val stale = executor.execute(before, AppCommandBatch(requestId = "stale-voice", expectedRevision = before.revision + 1,
            commands = listOf(AppCommand.StartVoice)), TestFixture.ai)
        assertFalse(stale.trace.accepted)
        assertTrue(stale.effects.isEmpty())
    }
    @Test fun speakEnablesAutomaticSubmissionAndRollsBackOnRejectedBatch() {
        val codec = AppCommandCodec()
        val before = TestFixture.reader().copy(preferences = UserPreferences(darkTheme = true, readingSizeSp = 28, voiceAutomaticSend = false))
        val batch = AppCommandBatch(requestId = "voice-mode", expectedRevision = before.revision,
            commands = listOf(AppCommand.StartVoice))
        val changed = executor.execute(before, codec.decode(codec.encode(batch)), TestFixture.ai)
        assertTrue(changed.trace.accepted)
        assertEquals(before.preferences.copy(voiceAutomaticSend = true), changed.state.preferences)
        val rejected = executor.execute(before, batch.copy(commands = batch.commands + AppCommand.OpenChapterLibrary("missing")), TestFixture.ai)
        assertFalse(rejected.trace.accepted)
        assertEquals(before.preferences, rejected.state.preferences)
    }
    @Test fun reviewOpensAssistantAndRequestsItsTranscriptSurface() {
        val result = execute(AppCommand.ReviewVoice)
        assertTrue(result.trace.accepted)
        assertEquals(AppDestination.ASSISTANT, result.state.navigation.destination)
        assertEquals(listOf(AppEffect.ReviewVoice), result.effects)
        assertFalse(execute(AppCommand.VoiceAutoSend(false)).trace.accepted)
    }
}
