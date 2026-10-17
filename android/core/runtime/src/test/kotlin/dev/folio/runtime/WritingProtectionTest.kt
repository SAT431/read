package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.SentenceIndex
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class WritingProtectionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://test.example"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private val draft = AnnotationDraft(TestFixture.source, text = "Private unfinished wording")
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    private fun reader(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    @Test fun independentEditorsCannotReleaseOneAnother() {
        val gate = InteractionGate()
        gate.set("first", true); gate.set("second", true)
        gate.set("first", false); assertTrue(gate.locked)
        gate.set("first", false); assertTrue(gate.locked)
        gate.set("second", false); assertFalse(gate.locked)
    }
    @Test fun restoredVisibleReaderDraftProtectsBeforeEditorsRecompose() {
        val gate = InteractionGate()
        val pending = LearningReducer(TestFixture.catalog).reduce(TestFixture.reader(),
            LearningInteraction.SaveAnnotationDraft("read", "new-p1", draft)).copy(
                navigation = dev.folio.model.workspace.AppNavigation(dev.folio.model.workspace.AppDestination.WORKSPACE))
        assertFalse(gate.locked)
        assertTrue(gate.blocks(pending))
        assertFalse(gate.blocks(pending.copy(navigation = dev.folio.model.workspace.AppNavigation())))
        assertFalse(gate.blocks(pending.copy(components = pending.components.mapValues { it.value.copy(visible = false) })))
    }
    @Test fun unfinishedDraftsArePrivateAndCannotBeMutatedByAi() {
        val command = AppCommand.SaveAnnotationDraft("read", "new-p1", draft)
        val batch = AppCommandBatch(requestId = "draft", commands = listOf(command))
        val saved = executor.execute(TestFixture.reader(), batch, TestFixture.user)
        assertTrue(saved.trace.accepted)
        assertEquals(draft, reader(saved.state).annotationDrafts.getValue("new-p1"))
        val context = AppCapabilityContext(curriculum).encode(saved.state, emptyList())
        assertFalse(context.contains(draft.text))
        val ai = executor.execute(saved.state, batch.copy(commands = listOf(command.copy(draft = null))), TestFixture.ai)
        assertFalse(ai.trace.accepted)
        assertEquals(saved.state, ai.state)
        val injected = WorkspaceCommand.CreateActivity("other", "other-view", dev.folio.model.ui.ComponentKind.READER,
            TestFixture.source, ActivityData.Reader(annotationDrafts = mapOf("draft" to draft)))
        assertFalse(TestFixture.execute(saved.state, injected, policy = TestFixture.ai).trace.accepted)
    }
    @Test fun savingNoteClearsOnlyItsMatchingDraftInTheSameTransaction() {
        val sentence = SentenceIndex().compile(requireNotNull(TestFixture.catalog.page("doc", 0))).first()
        val pending = draft.copy(sentenceId = sentence.id)
        val saved = executor.execute(TestFixture.reader(), AppCommandBatch(requestId = "drafts", commands = listOf(
            AppCommand.SaveAnnotationDraft("read", "new-p1", pending),
            AppCommand.SaveAnnotationDraft("read", "another", pending.copy(text = "Other draft")),
            AppCommand.CreateAnnotation("read", "saved", sentence.source, sentence.id, AnnotationKind.NOTE, pending.text)
        )), TestFixture.user)
        assertTrue(saved.trace.accepted)
        assertEquals(setOf("another"), reader(saved.state).annotationDrafts.keys)
        assertEquals(pending.text, reader(saved.state).annotations.getValue("saved").text)
    }
    @Test fun failedAnnotationSaveRetainsTheDraft() {
        val before = LearningReducer(TestFixture.catalog).reduce(TestFixture.reader(),
            LearningInteraction.SaveAnnotationDraft("read", "new-p1", draft))
        val failed = executor.execute(before, AppCommandBatch(requestId = "failed", commands = listOf(
            AppCommand.CreateAnnotation("read", "bad", TestFixture.source, "missing-sentence", AnnotationKind.NOTE, draft.text)
        )), TestFixture.user)
        assertFalse(failed.trace.accepted)
        assertEquals(before, failed.state)
    }
}
