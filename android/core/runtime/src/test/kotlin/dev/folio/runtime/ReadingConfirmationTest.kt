package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.*
import dev.folio.runtime.state.LayoutUndo
import org.junit.Assert.*
import org.junit.Test

class ReadingConfirmationTest {
    private var changed = false
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun page(documentId: String, pageIndex: Int): SourcePage = requireNotNull(TestFixture.catalog.page(documentId, pageIndex)).let { page ->
            page.copy(elements = page.elements.map { if (it.id == "p1") it.copy(originalText = "First sentence. Second sentence.") else it },
                corrections = if (changed) mapOf("p1" to "Changed sentence.") else emptyMap())
        }
    }
    private val reducer = ReadingConfirmationReducer(catalog) { 123L }
    private fun data(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    private fun confirm(state: Workspace = TestFixture.reader(), sentence: String? = null, actor: CommandActor = CommandActor.USER) = reducer.apply(state,
        LearningInteraction.ConfirmReading("read", TestFixture.source, sentence, data(state).readingRevision), actor)
    private fun progress(state: Workspace) = ReadingConfirmations.progress(data(state), catalog.page("doc", 0), catalog)

    @Test fun scrollingFocusAndBookmarkDoNotInventReadingConfirmation() {
        val sentence = SentenceIndex().compile(catalog.page("doc", 0)).first()
        val learning = LearningReducer(catalog)
        val scrolled = learning.reduce(TestFixture.reader(), LearningInteraction.SetReadingPosition("read", 1, 400))
        val focused = learning.reduce(scrolled, LearningInteraction.FocusSentence("read", sentence.id))
        val bookmarked = learning.reduce(focused, LearningInteraction.AddReadingBookmark("read", "bookmark", sentence.source, sentence.id))
        assertEquals(ReadingProgress(1, 0, 0, 0), progress(bookmarked))
        assertEquals(0L, data(bookmarked).readingRevision)
    }

    @Test fun individualSentencesCountPassageOnlyWhenAllAreExplicitlyReported() {
        val sentences = SentenceIndex().compile(catalog.page("doc", 0))
        val first = confirm(sentence = sentences[0].id)
        assertEquals(0, progress(first).confirmedPassages); assertEquals(1, progress(first).sentenceCheckpoints)
        val second = confirm(first, sentences[1].id)
        assertEquals(1, progress(second).confirmedPassages); assertEquals(2, progress(second).sentenceCheckpoints)
        assertEquals(ReadingConfirmations.id(TestFixture.source, sentences[1].id), progress(second).lastConfirmedId)
    }

    @Test fun passageReportHasNativeEvidenceAndAssistantActorWithoutFabricatedSentenceReports() {
        val state = confirm(actor = CommandActor.AI)
        val record = data(state).readingConfirmations.values.single()
        assertEquals(123L, record.reportedAtEpochMillis); assertEquals(1L, record.version)
        assertTrue(record.byAssistant); assertNull(record.sentenceId)
        assertEquals("First sentence. Second sentence.", record.excerpt)
        assertEquals(1, progress(state).confirmedPassages); assertEquals(0, progress(state).sentenceCheckpoints)
    }

    @Test fun staleClearCannotRemoveReconfirmedTargetAndStaleCreateRejects() {
        val first = confirm(); val record = data(first).readingConfirmations.values.single()
        val cleared = reducer.apply(first, LearningInteraction.ClearReadingConfirmation("read", record.id, record.version), CommandActor.USER)
        val again = confirm(cleared)
        assertEquals(3L, data(again).readingConfirmations.getValue(record.id).version)
        assertTrue(runCatching { reducer.apply(again, LearningInteraction.ClearReadingConfirmation("read", record.id, 1), CommandActor.AI) }.isFailure)
        assertTrue(runCatching { reducer.apply(again, LearningInteraction.ConfirmReading("read", TestFixture.source, null, 0), CommandActor.AI) }.isFailure)
        assertEquals(1, progress(again).confirmedPassages)
    }

    @Test fun wrongPageUnknownSentenceFiguresAndHiddenOrSuspendedReaderReject() {
        for (event in listOf(LearningInteraction.ConfirmReading("read", TestFixture.source.copy(pageIndex = 1), null, 0),
            LearningInteraction.ConfirmReading("read", TestFixture.source, "fake", 0),
            LearningInteraction.ConfirmReading("read", TestFixture.source.copy(elementId = "figure"), null, 0)))
            assertTrue(runCatching { reducer.apply(TestFixture.reader(), event, CommandActor.USER) }.isFailure)
        val hidden = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.SuspendActivity("read")).state
        assertTrue(runCatching { confirm(hidden) }.isFailure)
    }

    @Test fun changedWordingRetainsOldReportButExcludesItFromProgressAndMarker() {
        val first = confirm(); val record = data(first).readingConfirmations.values.single()
        changed = true
        assertEquals(ReadingProgress(1, 0, 0, 1), progress(first))
        assertEquals(record, data(first).readingConfirmations.getValue(record.id))
        ReadingConfirmations.validate(data(first))
        val refreshed = confirm(first)
        assertEquals(1, progress(refreshed).confirmedPassages); assertEquals(0, progress(refreshed).outdatedCheckpoints)
        assertEquals("Changed sentence.", data(refreshed).readingConfirmations.values.single().excerpt)
    }

    @Test fun clearingReportPreservesNotesBookmarksAnswersAndLayoutUndoKeepsReports() {
        val reader = TestFixture.reader()
        val noted = reader.copy(activities = reader.activities + ("read" to reader.activities.getValue("read").copy(
            data = data(reader).copy(notes = mapOf("private" to "Keep my original note")))))
        val initial = TestFixture.execute(noted, TestFixture.quiz()).state
        val learning = LearningReducer(catalog)
        val selected = learning.reduce(initial, LearningInteraction.SelectAnswer("quiz", "q1", 1))
        val saved = learning.reduce(selected, LearningInteraction.AddReadingBookmark("read", "bookmark", TestFixture.source))
        val confirmed = confirm(saved)
        val undo = LayoutUndo(); undo.record(initial)
        val restored = requireNotNull(undo.restore(confirmed))
        assertEquals(data(confirmed).readingConfirmations, data(restored).readingConfirmations)
        val record = data(restored).readingConfirmations.values.single()
        val cleared = reducer.apply(restored, LearningInteraction.ClearReadingConfirmation("read", record.id, record.version), CommandActor.USER)
        assertEquals(data(saved).bookmarks, data(cleared).bookmarks)
        assertEquals(mapOf("private" to "Keep my original note"), data(cleared).notes)
        assertEquals(mapOf("q1" to 1), (cleared.activities.getValue("quiz").data as ActivityData.Quiz).answers)
    }

    @Test fun malformedRecordsAndAiCreationInjectionReject() {
        val saved = confirm(); val reader = data(saved); val record = reader.readingConfirmations.values.single()
        assertTrue(runCatching { ReadingConfirmations.validate(reader.copy(readingRevision = 0)) }.isFailure)
        assertTrue(runCatching { ReadingConfirmations.validate(reader.copy(readingConfirmations = mapOf("fake" to record))) }.isFailure)
        assertTrue(runCatching { ReadingConfirmations.validate(reader.copy(readingConfirmations = mapOf(record.id to record.copy(reportedAtEpochMillis = -1)))) }.isFailure)
        val result = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.CreateActivity("injected", "injected-view", ComponentKind.READER,
            TestFixture.source, reader), policy = TestFixture.ai)
        assertFalse(result.trace.accepted)
    }

    @Test fun emptyOrNonReadingElementsDoNotInventAProgressDenominator() {
        val page = catalog.page("doc", 0).copy(elements = listOf(
            SourceElement("heading", SourceElementKind.HEADING, "Heading", 0),
            SourceElement("empty", SourceElementKind.CAPTION, "   ", 1)))
        assertEquals(ReadingProgress(0, 0, 0, 0), ReadingConfirmations.progress(ActivityData.Reader(), page, catalog))
    }
}
