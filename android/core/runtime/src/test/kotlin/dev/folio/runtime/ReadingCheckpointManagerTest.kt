package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.navigation.SourceNavigator
import dev.folio.runtime.reading.*
import org.junit.Assert.*
import org.junit.Test

class ReadingCheckpointManagerTest {
    private var changed = false
    private var missing = false
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun page(documentId: String, pageIndex: Int): SourcePage? = if (missing) null else
            TestFixture.catalog.page(documentId, pageIndex)?.let { if (changed && pageIndex == 0)
                it.copy(corrections = mapOf("p1" to "Changed wording.")) else it }
    }
    private val reducer = ReadingConfirmationReducer(catalog) { 123L }
    private fun data(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    private fun saved(): Workspace {
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page("doc", 0))).first()
        return reducer.apply(TestFixture.reader(), LearningInteraction.ConfirmReading("read", sentence.source, sentence.id, 0), CommandActor.USER)
    }
    private fun open(state: Workspace, actor: CommandActor = CommandActor.USER): Workspace {
        val record = data(state).readingConfirmations.values.single()
        return reducer.apply(state, LearningInteraction.OpenReadingConfirmation("read", record.id, record.version), actor)
    }

    @Test fun showingManagerDoesNotCreateOrRefreshAnyReport() {
        val saved = saved()
        val shown = reducer.apply(saved, LearningInteraction.ShowReadingCheckpoints("read", true), CommandActor.AI)
        assertTrue(data(shown).readingCheckpointsVisible)
        assertEquals(data(saved).readingConfirmations, data(shown).readingConfirmations)
        assertEquals(data(saved).readingRevision, data(shown).readingRevision)
    }
    @Test fun openingFromAnotherPageRestoresExactSentenceWithoutRefreshingReport() {
        val saved = saved()
        val moved = SourceNavigator(catalog).open(saved, SourceAnchor("doc", 2))
        val opened = open(moved, CommandActor.AI)
        val record = data(saved).readingConfirmations.values.single()
        assertEquals(record.source, opened.context.source); assertEquals(record.sentenceId, data(opened).selectedSentence)
        assertEquals(0, data(opened).scrollIndex); assertEquals(1, data(opened).bookmarkJump)
        assertEquals(data(saved).readingConfirmations, data(opened).readingConfirmations)
        assertEquals(data(saved).readingRevision, data(opened).readingRevision)
    }
    @Test fun openingResumesHiddenCapsuleAndPreservesOtherReaderData() {
        val saved = saved()
        val bookmarked = dev.folio.runtime.interaction.LearningReducer(catalog).reduce(saved,
            LearningInteraction.AddReadingBookmark("read", "bookmark", TestFixture.source))
        val hidden = TestFixture.execute(bookmarked, WorkspaceCommand.SetPresentation("read-view", Presentation.CAPSULE),
            WorkspaceCommand.SuspendActivity("read")).state
        val opened = open(hidden)
        assertEquals(ActivityStatus.ACTIVE, opened.activities.getValue("read").status)
        assertTrue(opened.components.getValue("read-view").visible)
        assertEquals(Presentation.SURFACE, opened.components.getValue("read-view").presentation)
        assertEquals(data(bookmarked).bookmarks, data(opened).bookmarks)
    }
    @Test fun staleVersionAndChangedWordingRejectWithoutDiscardingEvidence() {
        val saved = saved(); val record = data(saved).readingConfirmations.values.single()
        assertTrue(runCatching { reducer.apply(saved, LearningInteraction.OpenReadingConfirmation("read", record.id, 999), CommandActor.USER) }.isFailure)
        changed = true
        assertTrue(runCatching { open(saved) }.isFailure)
        assertEquals(record, data(saved).readingConfirmations.getValue(record.id))
        val cleared = reducer.apply(saved, LearningInteraction.ClearReadingConfirmation("read", record.id, record.version), CommandActor.USER)
        assertTrue(data(cleared).readingConfirmations.isEmpty())
    }
    @Test fun missingPageDoesNotBlockShowingManagerOrClearingSavedReport() {
        val saved = saved(); val record = data(saved).readingConfirmations.values.single()
        missing = true
        val shown = reducer.apply(saved, LearningInteraction.ShowReadingCheckpoints("read", true), CommandActor.USER)
        assertTrue(data(shown).readingCheckpointsVisible)
        assertTrue(runCatching { open(shown) }.isFailure)
        val cleared = reducer.apply(shown, LearningInteraction.ClearReadingConfirmation("read", record.id, record.version), CommandActor.USER)
        assertTrue(data(cleared).readingConfirmations.isEmpty()); assertTrue(data(cleared).readingCheckpointsVisible)
    }
    @Test fun aiCannotOpenCheckpointThroughAnotherPinnedViewOfSharedReader() {
        val saved = saved()
        val extra = ComponentInstance("second-view", "read", ComponentKind.READER, pinned = true)
        val pinned = saved.copy(components = saved.components + (extra.id to extra))
        assertTrue(runCatching { open(pinned, CommandActor.AI) }.isFailure)
        assertEquals(data(saved).readingConfirmations, data(open(pinned)).readingConfirmations)
    }
}
