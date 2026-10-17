package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.source.*
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.search.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StudySearchTest {
    private var page = requireNotNull(TestFixture.catalog.page("doc", 0)).let { it.copy(isFixture = false,
        elements = it.elements.map { element -> if (element.id == "p1") element.copy(originalText = "Cell membrane.") else element }) }
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun page(documentId: String, pageIndex: Int) = page.takeIf { documentId == "doc" && pageIndex == 0 }
        override fun cachedPages() = listOf(page)
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "fixture"))
        override fun chapters() = (0..44).map { StudyChapter("cell-$it", SubjectId.BOTANY, "Cell study $it", 1, it) }
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "cell-0", TestFixture.catalog.documents().first().copy(title = "Cell chapter.pdf"), 1)))
        override suspend fun load() {}
    }
    private val engine = StudySearchEngine(curriculum, catalog)
    private val app = AppCommandExecutor(TestFixture.executor, curriculum, library, catalog)
    private fun state(): Workspace {
        var state = TestFixture.execute(TestFixture.reader(),
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, TestFixture.source, ActivityData.Quiz(listOf(
                Question("q", "Cell quiz", listOf("a", "b", "c", "d"), 0, "hidden-explanation-needle", listOf(TestFixture.source))))),
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, TestFixture.source,
                ActivityData.Recall("Cell recall", "hidden-reference-needle", listOf(TestFixture.source), response = "private-answer-needle"))).state
        state = LearningReducer(catalog).reduce(state, LearningInteraction.AddReadingBookmark("read", "saved", TestFixture.source))
        val activity = state.activities.getValue("read"); val reader = activity.data as ActivityData.Reader
        return state.copy(activities = state.activities + ("read" to activity.copy(data = reader.copy(
            annotations = mapOf("note" to SourceAnnotation("note", TestFixture.source, kind = AnnotationKind.NOTE, text = "Cell saved note")),
            annotationDrafts = mapOf("private" to AnnotationDraft(TestFixture.source, text = "secret-draft-needle"))))),
            navigation = AppNavigation(AppDestination.SEARCH, globalQuery = "cell"))
    }
    private fun search(state: Workspace, text: String, kind: SearchKind? = null) = engine.search(state.copy(navigation = state.navigation.copy(globalQuery = text, searchKind = kind, searchPage = 0)), library.entries.value)
    private fun open(state: Workspace, result: SearchResult, actor: CommandActor = CommandActor.USER) = app.execute(state,
        AppCommandBatch(requestId = "open", commands = listOf(AppCommand.OpenSearchResult(result.target, result.fingerprint))), ExecutionPolicy(actor))
    @Test fun allCategoriesAreSearchedAndPaginationHasStableDistinctTargets() {
        val state = state(); val first = engine.search(state, library.entries.value)
        val second = engine.search(state.copy(navigation = state.navigation.copy(searchPage = 1)), library.entries.value)
        assertEquals(40, first.results.size); assertTrue(first.total > 40); assertEquals(first.total - 40, second.results.size)
        val all = first.results + second.results
        assertEquals(SearchKind.entries.toSet(), all.map { it.target.kind }.toSet())
        assertEquals(all.size, all.map { it.target }.distinct().size)
        assertEquals(first, engine.search(state, library.entries.value))
    }
    @Test fun privateDraftsHiddenReferencesAndExplanationsCannotBecomeSearchResults() {
        val state = state()
        for (needle in listOf("secret-draft-needle", "hidden-reference-needle", "private-answer-needle", "hidden-explanation-needle"))
            assertEquals(needle, 0, search(state, needle).total)
        assertEquals(1, search(state, "cell   membrane", SearchKind.PASSAGE).total)
        assertEquals(1, search(state, "Ｃｅｌｌ", SearchKind.NOTE).total)
    }
    @Test fun importedPdfChapterAndPassageOpenThroughExactNativeTargets() {
        val state = state()
        for ((kind, destination) in listOf(SearchKind.PDF to AppDestination.PDF, SearchKind.CHAPTER to AppDestination.CHAPTER, SearchKind.PASSAGE to AppDestination.WORKSPACE)) {
            val result = open(state, search(state, "cell", kind).results.first())
            assertTrue(result.trace.error, result.trace.accepted); assertEquals(destination, result.state.navigation.destination)
            assertEquals((state.activities.getValue("read").data as ActivityData.Reader).annotations,
                (result.state.activities.getValue("read").data as ActivityData.Reader).annotations)
        }
    }
    @Test fun missingSourceNotesRemainViewableWhileOutdatedBookmarksStayRetainedAndDisabled() {
        val state = state(); page = page.copy(elements = emptyList())
        val note = search(state, "cell", SearchKind.NOTE).results.single(); assertTrue(note.canOpen)
        val opened = open(state, note); assertTrue(opened.trace.error, opened.trace.accepted); assertEquals(AppDestination.NOTES, opened.state.navigation.destination)
        val bookmark = search(state, "cell", SearchKind.BOOKMARK).results.single(); assertFalse(bookmark.canOpen)
        assertFalse(open(state, bookmark).trace.accepted)
        assertEquals(state.activities, opened.state.activities)
    }
    @Test fun changedPassageNoteAndMalformedTargetsRejectWithoutCrashingOrChangingState() {
        val state = state(); val result = search(state, "cell", SearchKind.PASSAGE).results.single()
        page = page.copy(elements = page.elements.map { if (it.id == "p1") it.copy(originalText = "Changed source.") else it })
        assertFalse(open(state, result).trace.accepted)
        val bad = app.execute(state, AppCommandBatch(requestId = "bad", commands = listOf(AppCommand.OpenSearchResult(SearchTarget(SearchKind.QUIZ, "missing", "q", attemptNumber = 99), "fake"))), TestFixture.ai)
        assertFalse(bad.trace.accepted); assertEquals(state, bad.state)
        val note = search(state, "cell", SearchKind.NOTE).results.single()
        val reader = state.activities.getValue("read").data as ActivityData.Reader
        val changed = state.copy(activities = state.activities + ("read" to state.activities.getValue("read").copy(data = reader.copy(annotations = mapOf("note" to reader.annotations.getValue("note").copy(text = "Changed note"))))))
        assertFalse(open(changed, note).trace.accepted)
    }
    @Test fun searchAndPageCommandsAreBoundedAtomicAndWireRoundTripWithoutProviderCalls() {
        val state = state(); val batch = AppCommandBatch(requestId = "query", commands = listOf(AppCommand.SearchStudy("cell", SearchKind.PASSAGE), AppCommand.SearchPage(1)))
        val codec = AppCommandCodec(); assertEquals(batch, codec.decode(codec.encode(batch)))
        val result = app.execute(state, batch, TestFixture.ai); assertTrue(result.trace.accepted); assertTrue(result.effects.isEmpty())
        val rejected = app.execute(state, batch.copy(commands = listOf(AppCommand.SearchStudy("x".repeat(201)))), TestFixture.user)
        assertFalse(rejected.trace.accepted); assertEquals(state, rejected.state)
        assertFalse(app.execute(state, batch.copy(commands = listOf(AppCommand.SearchPage(-1))), TestFixture.user).trace.accepted)
    }
    @Test fun historicalQuizResultIsReadOnlyAndRecallOpenDoesNotRevealOrReplaceResponse() {
        val start = state()
        var checked = LearningReducer(catalog).reduce(start, LearningInteraction.SelectAnswer("quiz", "q", 1))
        checked = LearningReducer(catalog).reduce(checked, LearningInteraction.SubmitAnswer("quiz", "q"))
        val restarted = app.execute(checked, AppCommandBatch(requestId = "restart", commands = listOf(AppCommand.RestartQuiz("quiz", 1))), TestFixture.user)
        assertTrue(restarted.trace.error, restarted.trace.accepted)
        val entries = search(restarted.state, "cell", SearchKind.QUIZ).results
        assertEquals(setOf(1, 2), entries.map { it.target.attemptNumber }.toSet())
        val historical = open(restarted.state, entries.first { it.target.attemptNumber == 1 })
        assertTrue(historical.trace.error, historical.trace.accepted)
        val quiz = historical.state.activities.getValue("quiz").data as ActivityData.Quiz
        assertEquals(1, quiz.review!!.attemptNumber); assertTrue(quiz.answers.isEmpty())
        assertEquals(mapOf("q" to 1), quiz.pastAttempts.single().answers)
        val recall = open(start, search(start, "cell", SearchKind.RECALL).results.single())
        assertTrue(recall.trace.error, recall.trace.accepted)
        assertEquals(start.activities.getValue("recall").data, recall.state.activities.getValue("recall").data)
    }
    @Test fun pinnedReaderRejectsAiPassageOpenAndNativeOpenKeepsBookmarksAndDrafts() {
        val state = state().let { it.copy(components = it.components.mapValues { (_, view) -> if (view.activityId == "read") view.copy(pinned = true) else view }) }
        val target = search(state, "cell", SearchKind.PASSAGE).results.single()
        assertFalse(open(state, target, CommandActor.AI).trace.accepted)
        val opened = open(state, target); assertTrue(opened.trace.error, opened.trace.accepted)
        val before = state.activities.getValue("read").data as ActivityData.Reader
        val after = opened.state.activities.getValue("read").data as ActivityData.Reader
        assertEquals(before.bookmarks, after.bookmarks); assertEquals(before.annotationDrafts, after.annotationDrafts)
        assertEquals(before.readingConfirmations, after.readingConfirmations)
    }
    @Test fun fixturePassagesAreExcludedAndSearchCancellationPropagates() {
        val state = state(); page = page.copy(isFixture = true)
        assertEquals(0, search(state, "cell", SearchKind.PASSAGE).total)
        assertTrue(runCatching { engine.search(state, library.entries.value) { throw kotlinx.coroutines.CancellationException() } }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
    @Test fun searchProviderSnapshotAndNavigationRedactTypedQueryAndExposeOnlyBoundedResults() {
        val state = state(); val snapshot = engine.search(state, library.entries.value)
        val encoded = AppCapabilityContext(curriculum, sourceCatalog = catalog, searchSnapshot = { snapshot }).encode(state, library.entries.value)
        val parsed = Json.parseToJsonElement(encoded).jsonObject
        assertEquals("", parsed.getValue("navigation").jsonObject.getValue("globalQuery").jsonPrimitive.content)
        assertEquals("", parsed.getValue("search").jsonObject.getValue("query").jsonPrimitive.content)
        assertEquals(40, parsed.getValue("search").jsonObject.getValue("results").jsonArray.size)
        assertFalse(parsed.getValue("searchPending").jsonPrimitive.boolean)
        assertFalse(encoded.contains("secret-draft-needle"))
    }
}
