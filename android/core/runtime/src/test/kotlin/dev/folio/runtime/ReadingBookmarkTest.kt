package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.*
import dev.folio.runtime.navigation.SourceNavigator
import dev.folio.runtime.validation.ActivityValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReadingBookmarkTest {
    private var changed = false
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun page(documentId: String, pageIndex: Int): SourcePage = requireNotNull(TestFixture.catalog.page(documentId, pageIndex)).let {
            if (changed && pageIndex == 0) it.copy(corrections = mapOf("p1" to "Changed wording.")) else it
        }
    }
    private val reducer = ReadingBookmarkReducer(catalog) { 123L }
    private fun data(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    private fun add(state: Workspace = TestFixture.reader(), sentence: String? = null, source: SourceAnchor = TestFixture.source,
        id: String = "bookmark", actor: CommandActor = CommandActor.USER) = reducer.apply(state,
        LearningInteraction.AddReadingBookmark("read", id, source, sentence), actor)
    private fun open(state: Workspace, actor: CommandActor = CommandActor.USER) = reducer.apply(state,
        LearningInteraction.OpenReadingBookmark("read", "bookmark", data(state).bookmarks.getValue("bookmark").createdAtRevision), actor)

    @Test fun sentenceSaveUsesNativeTimeActorAndExactSourceIdentity() {
        val sentence = SentenceIndex().compile(catalog.page("doc", 0)).single().id
        val saved = add(sentence = sentence, actor = CommandActor.AI)
        val item = data(saved).bookmarks.getValue("bookmark")
        assertEquals(TestFixture.source, item.source); assertEquals(sentence, item.sentenceId)
        assertEquals(123L, item.createdAtEpochMillis); assertEquals(saved.revision, item.createdAtRevision)
        assertTrue(item.byAssistant); assertEquals("Source.", item.excerpt)
        val elsewhere = SourceNavigator(catalog).open(saved, SourceAnchor("doc", 1))
        val returned = open(elsewhere)
        assertEquals(TestFixture.source, returned.context.source)
        assertEquals(sentence, data(returned).selectedSentence)
        assertEquals(1, data(returned).bookmarkJump)
        assertEquals(item, data(returned).bookmarks.getValue("bookmark"))
    }

    @Test fun pagePositionSaveResolvesTopVisibleElementAndOffsetInsteadOfTrustingModelCoordinates() {
        val positioned = LearningReducer(catalog).reduce(TestFixture.reader(), LearningInteraction.SetReadingPosition("read", 1, 37))
        val saved = add(positioned, source = SourceAnchor("doc", 0))
        val item = data(saved).bookmarks.getValue("bookmark")
        assertEquals("figure", item.source.elementId); assertEquals(37, item.scrollOffset)
        val opened = open(SourceNavigator(catalog).open(saved, SourceAnchor("doc", 2)))
        assertEquals(1, data(opened).scrollIndex); assertEquals(37, data(opened).scrollOffset)
        assertEquals(ReadingPosition(1, 37), data(opened).pagePositions["doc:0"])
    }

    @Test fun staleRecordsAndWrongPageOrSentenceRejectWithoutMutatingSavedWork() {
        val saved = add()
        assertTrue(runCatching { add(saved, id = "duplicate") }.isFailure)
        assertTrue(runCatching { add(source = SourceAnchor("doc", 1, "p1")) }.isFailure)
        assertTrue(runCatching { add(sentence = "invented") }.isFailure)
        for (event in listOf(LearningInteraction.OpenReadingBookmark("read", "bookmark", 999),
            LearningInteraction.RemoveReadingBookmark("read", "bookmark", 999)))
            assertTrue(runCatching { reducer.apply(saved, event, CommandActor.USER) }.isFailure)
        assertEquals(1, data(saved).bookmarks.size)
    }

    @Test fun changedWordingRetainsRecordButRejectsRetargetingAndAllowsExplicitRemoval() {
        val saved = add()
        changed = true
        assertTrue(runCatching { open(saved) }.isFailure)
        ActivityValidator.validate(data(saved), TestFixture.source, catalog)
        assertEquals("Source.", data(saved).bookmarks.getValue("bookmark").excerpt)
        val removed = reducer.apply(saved, LearningInteraction.RemoveReadingBookmark("read", "bookmark", saved.revision), CommandActor.USER)
        assertTrue(data(removed).bookmarks.isEmpty())
    }

    @Test fun hiddenReaderCanResumeAtBookmarkWithoutDiscardingNotesButCannotSaveWhileHidden() {
        val noted = TestFixture.reader().let { state -> state.copy(activities = state.activities + ("read" to state.activities.getValue("read")
            .copy(data = data(state).copy(notes = mapOf("private-note" to "Keep this"))))) }
        val saved = add(noted)
        val hidden = TestFixture.execute(saved, WorkspaceCommand.SuspendActivity("read")).state
        assertTrue(runCatching { add(hidden, id = "second") }.isFailure)
        val resumed = open(hidden)
        assertEquals(ActivityStatus.ACTIVE, resumed.activities.getValue("read").status)
        assertTrue(resumed.components.getValue("read-view").visible)
        assertEquals(data(noted).notes, data(resumed).notes)
        assertEquals(data(saved).bookmarks, data(resumed).bookmarks)
    }

    @Test fun aiOpenProtectsEveryPinnedViewOfSharedReaderAndCannotInjectBookmarksOnCreation() {
        val saved = add()
        val second = ComponentInstance("second", "read", ComponentKind.READER, pinned = true)
        val pinned = saved.copy(components = saved.components + (second.id to second))
        assertTrue(runCatching { open(pinned, CommandActor.AI) }.isFailure)
        assertEquals(TestFixture.source, open(pinned).context.source)
        val injected = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.CreateActivity("injected", "injected-view", ComponentKind.READER,
            TestFixture.source, data(saved)), policy = TestFixture.ai)
        assertFalse(injected.trace.accepted)
    }

    @Test fun sharedCommandWireRetainsTargetGuardsAndStructuralValidationBoundsRecords() {
        val actions = listOf<AppCommand>(AppCommand.AddReadingBookmark("read", "new", TestFixture.source),
            AppCommand.OpenReadingBookmark("read", "old", 10), AppCommand.RemoveReadingBookmark("read", "old", 10),
            AppCommand.ShowReadingBookmarks("read", true))
        val json = Json { encodeDefaults = true }
        for (command in actions) {
            assertEquals(command, json.decodeFromString<AppCommand>(json.encodeToString(command)))
            assertEquals(command, dev.folio.runtime.action.LearningCommandBridge.command(requireNotNull(dev.folio.runtime.action.LearningCommandBridge.event(command))))
        }
        val record = data(add()).bookmarks.getValue("bookmark")
        assertTrue(runCatching { ReadingBookmarks.validate(mapOf(record.id to record.copy(textFingerprint = "fake"))) }.isFailure)
        assertTrue(runCatching { ReadingBookmarks.validate(mapOf(record.id to record.copy(scrollOffset = -1))) }.isFailure)
        assertTrue(runCatching { ReadingBookmarks.validate(mapOf("wrong-id" to record)) }.isFailure)
    }
}
