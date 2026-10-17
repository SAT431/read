package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.reading.SentenceIndex
import dev.folio.runtime.state.LayoutUndo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReadingBookmarkActionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"))
        override fun chapters() = listOf(StudyChapter("unit", SubjectId.BOTANY, "Cells", 1, 1))
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "unit", TestFixture.catalog.documents().single(), 0)))
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    private fun data(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    private fun execute(state: Workspace, vararg commands: AppCommand) = executor.execute(state,
        AppCommandBatch(requestId = "bookmark", commands = commands.toList()), TestFixture.ai)
    private val add = AppCommand.AddReadingBookmark("read", "bookmark", TestFixture.source)

    @Test fun failedMultiActionBatchCannotLeaveHalfSavedBookmark() {
        val initial = TestFixture.reader()
        val rejected = execute(initial, add, AppCommand.OpenReadingBookmark("read", "bookmark", 999))
        assertFalse(rejected.trace.accepted); assertEquals(initial, rejected.state)
    }

    @Test fun sourceAwareOpenUpdatesPdfAndPreservesQuizAnswersAndLayoutUndoDoesNotDeleteBookmark() {
        val initial = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        val selected = execute(initial, AppCommand.SelectAnswer("quiz", "q1", 1)).state
        val saved = execute(selected, add)
        assertTrue(saved.trace.error, saved.trace.accepted)
        val revision = data(saved.state).bookmarks.getValue("bookmark").createdAtRevision
        val moved = TestFixture.execute(saved.state, WorkspaceCommand.OpenPage("doc", 2)).state
        val opened = execute(moved, AppCommand.OpenReadingBookmark("read", "bookmark", revision))
        assertTrue(opened.trace.error, opened.trace.accepted)
        assertEquals("pdf", opened.state.navigation.pdfId); assertEquals("unit", opened.state.navigation.chapterId)
        assertEquals(SubjectId.BOTANY, opened.state.navigation.subject); assertEquals(0, opened.state.navigation.pdfPageIndex)
        assertEquals(TestFixture.source, opened.state.context.source)
        assertEquals(mapOf("q1" to 1), (opened.state.activities.getValue("quiz").data as ActivityData.Quiz).answers)
        val undo = LayoutUndo(); undo.record(initial)
        assertEquals(data(opened.state).bookmarks, data(requireNotNull(undo.restore(opened.state))).bookmarks)
    }

    @Test fun actualProviderContextContainsNativeBookmarkAndSentenceTargetWithoutPrivateDraft() {
        val sentence = SentenceIndex().compile(requireNotNull(TestFixture.catalog.page("doc", 0))).single()
        val saved = execute(TestFixture.reader(), add.copy(sentenceId = sentence.id))
        assertTrue(saved.trace.error, saved.trace.accepted)
        val reader = saved.state.activities.getValue("read")
        val privateState = saved.state.copy(activities = saved.state.activities + (reader.id to reader.copy(data = data(saved.state).copy(
            annotationDrafts = mapOf("draft" to dev.folio.model.source.AnnotationDraft(TestFixture.source, text = "Private unfinished bookmark-context note"))))))
        val encoded = AppCapabilityContext(curriculum, sourceCatalog = TestFixture.catalog).encode(privateState, library.entries.value)
        assertFalse(encoded.contains("Private unfinished bookmark-context note"))
        val context = Json.parseToJsonElement(encoded).jsonObject
        val bookmark = context.getValue("activities").jsonObject.getValue("read").jsonObject.getValue("data").jsonObject
            .getValue("bookmarks").jsonObject.getValue("bookmark").jsonObject
        assertEquals(sentence.id, bookmark.getValue("sentenceId").jsonPrimitive.content)
        assertTrue(bookmark.getValue("byAssistant").jsonPrimitive.boolean)
        assertEquals("p1", bookmark.getValue("source").jsonObject.getValue("elementId").jsonPrimitive.content)
        assertTrue(context.getValue("activities").jsonObject.getValue("read").jsonObject.getValue("data").jsonObject.getValue("annotationDrafts").jsonObject.isEmpty())
        val actions = context.getValue("actions").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertTrue(actions.containsAll(listOf("reader.bookmark.add", "reader.bookmark.open", "reader.bookmark.remove", "reader.bookmarks.show")))
    }

    @Test fun reusingIdAcrossAtomicBatchesCannotMakeOldOpenOrRemoveMatchReplacement() {
        val sentence = SentenceIndex().compile(requireNotNull(TestFixture.catalog.page("doc", 0))).single().id
        val created = execute(TestFixture.reader(), add.copy(id = "first"), add.copy(id = "second", source = SourceAnchor("doc", 0, "figure")),
            add.copy(sentenceId = sentence))
        assertTrue(created.trace.error, created.trace.accepted)
        val old = data(created.state).bookmarks.getValue("bookmark")
        assertEquals(3L, old.createdAtRevision)
        assertEquals(1L, created.state.revision)
        val replaced = execute(created.state, AppCommand.RemoveReadingBookmark("read", "bookmark", old.createdAtRevision), add.copy(sentenceId = sentence))
        assertTrue(replaced.trace.error, replaced.trace.accepted)
        assertEquals(5L, data(replaced.state).bookmarks.getValue("bookmark").createdAtRevision)
        for (command in listOf(AppCommand.OpenReadingBookmark("read", "bookmark", old.createdAtRevision),
            AppCommand.RemoveReadingBookmark("read", "bookmark", old.createdAtRevision))) {
            val stale = execute(replaced.state, command)
            assertFalse(stale.trace.accepted); assertEquals(replaced.state, stale.state)
        }
    }
}
