package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.*
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.state.LayoutUndo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SourceAnnotationTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://test.example"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    private fun create(id: String = "note") = AppCommand.CreateAnnotation("read", id, TestFixture.source,
        SentenceIndex().compile(requireNotNull(TestFixture.catalog.page("doc", 0))).first().id, AnnotationKind.NOTE, "Remember this")
    private fun apply(state: Workspace, vararg commands: AppCommand, ai: Boolean = false) = executor.execute(state,
        AppCommandBatch(requestId = "annotation", expectedRevision = state.revision, commands = commands.toList()),
        if (ai) TestFixture.ai else TestFixture.user)
    private fun annotations(state: Workspace) = (state.activities.getValue("read").data as ActivityData.Reader).annotations

    @Test fun addsEditsAndRemovesWithoutChangingSourceText() {
        val original = TestFixture.catalog.page("doc", 0)
        val added = apply(TestFixture.reader(), create())
        assertTrue(added.trace.accepted)
        assertEquals(AnnotationAuthor.USER, annotations(added.state).getValue("note").author)
        val changed = apply(added.state, AppCommand.UpdateAnnotation("read", "note", "Remember this", "Revise tomorrow"))
        assertTrue(changed.trace.accepted)
        assertEquals("Revise tomorrow", annotations(changed.state).getValue("note").text)
        val removed = apply(changed.state, AppCommand.RemoveAnnotation("read", "note", "Revise tomorrow"))
        assertTrue(annotations(removed.state).isEmpty())
        assertEquals(original, TestFixture.catalog.page("doc", 0))
    }
    @Test fun staleEditAndDeleteCannotOverwriteANewerNote() {
        val added = apply(TestFixture.reader(), create()).state
        val changed = apply(added, AppCommand.UpdateAnnotation("read", "note", "Remember this", "New text")).state
        for (command in listOf(AppCommand.UpdateAnnotation("read", "note", "Remember this", "Stale"),
            AppCommand.RemoveAnnotation("read", "note", "Remember this"))) {
            val rejected = apply(changed, command, ai = true)
            assertFalse(rejected.trace.accepted)
            assertEquals(changed, rejected.state)
        }
    }
    @Test fun wrongPageSentenceAndDuplicateIdsRejectWholeBatch() {
        val before = TestFixture.reader()
        for (command in listOf(create("wrong").copy(source = TestFixture.source.copy(pageIndex = 1)),
            create("wrong").copy(sentenceId = "invented"), create())) {
            val result = apply(before, create(), command)
            assertFalse(result.trace.accepted)
            assertEquals(before, result.state)
        }
    }
    @Test fun aiProvenanceIsStampedAndCannotBeForgedThroughReaderCreation() {
        val added = apply(TestFixture.reader(), create(), ai = true)
        assertEquals(AnnotationAuthor.AI, annotations(added.state).getValue("note").author)
        val forged = WorkspaceCommand.CreateActivity("other", "other-view", dev.folio.model.ui.ComponentKind.READER,
            TestFixture.source, ActivityData.Reader(annotations = annotations(added.state)))
        assertFalse(TestFixture.execute(added.state, forged, policy = TestFixture.ai).trace.accepted)
    }
    @Test fun sourceNotesSurviveSuspensionPageChangeAndLayoutUndo() {
        val before = TestFixture.reader()
        val undo = LayoutUndo().apply { record(before) }
        val added = apply(before, create(), create("mark").copy(kind = AnnotationKind.HIGHLIGHT, text = "")).state
        val suspended = TestFixture.execute(added, WorkspaceCommand.SuspendActivity("read")).state
        val resumed = TestFixture.execute(suspended, WorkspaceCommand.ResumeActivity("read"), WorkspaceCommand.NextPage).state
        val restored = requireNotNull(undo.restore(resumed))
        assertEquals(annotations(added), annotations(restored))
        assertEquals(1, restored.activities.getValue("read").source.pageIndex)
    }
    @Test fun annotationCommandsRoundTripStrictWireContract() {
        val codec = AppCommandCodec()
        val batch = AppCommandBatch(requestId = "roundtrip", commands = listOf(create(),
            AppCommand.UpdateAnnotation("read", "note", "Remember this", "Updated"),
            AppCommand.RemoveAnnotation("read", "note", "Updated")))
        assertEquals(batch, codec.decode(codec.encode(batch)))
    }
}
