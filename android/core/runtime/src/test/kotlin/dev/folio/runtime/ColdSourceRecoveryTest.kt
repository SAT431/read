package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ColdSourceRecoveryTest {
    private var missing = false
    private var missingDocument = false
    private val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun documents() = if (missingDocument) emptyList() else TestFixture.catalog.documents()
        override fun page(documentId: String, pageIndex: Int) = if (missing || missingDocument) null else TestFixture.catalog.page(documentId, pageIndex)
        override fun contains(anchor: SourceAnchor) = !missingDocument && TestFixture.catalog.contains(anchor) && (!missing || anchor.elementId == null)
    }
    private fun saved(): Workspace {
        val initial = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        val learning = LearningReducer(catalog) { 10L }
        val selected = learning.reduce(initial, LearningInteraction.SelectAnswer("quiz", "q1", 1))
        val bookmark = learning.reduce(selected, LearningInteraction.AddReadingBookmark("read", "bookmark", TestFixture.source))
        return learning.reduce(bookmark, LearningInteraction.ConfirmReading("read", TestFixture.source, expectedReadingRevision = 0))
    }
    private class Store(var value: Workspace) : WorkspaceRepository {
        override suspend fun load() = value
        override suspend fun save(workspace: Workspace) { value = workspace }
    }
    private fun session(store: Store) = WorkspaceSession(Workspace(), CommandExecutor(TestFixture.registry, catalog), store,
        WorkspaceValidator(TestFixture.registry, catalog), reducer = LearningReducer(catalog))
    @Test fun missingPageRestoresSavedAnswersAndRecordsWithoutRetargetingSources() = runBlocking {
        val saved = saved(); val store = Store(saved); missing = true
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = WorkspaceSession(Workspace(), CommandExecutor(TestFixture.registry, catalog), store, WorkspaceValidator(TestFixture.registry, catalog))
        session.attachWriter(scope)
        try {
            session.restore()
            assertTrue("Saved quiz was hidden by missing source data", "quiz" in session.state.value.activities)
            assertEquals(saved, session.state.value)
            assertNull(session.storageError.value)
            assertTrue(TestFixture.source in session.recoveredUnavailableSources.value)
            val result = session.dispatch(CommandBatch(requestId = "resume", commands = listOf(WorkspaceCommand.ResumeActivity("read"))), TestFixture.user)
            assertTrue(result.trace.error, result.trace.accepted)
            assertEquals(saved.activities, store.value.activities)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun absentDocumentMetadataStillRetainsSavedWorkAndItsUnchangedReferences() = runBlocking {
        val saved = saved(); missingDocument = true
        val restored = session(Store(saved)); restored.restore()
        assertNull(restored.storageError.value); assertEquals(saved, restored.state.value)
        assertTrue(restored.recoveredUnavailableSources.value.contains(TestFixture.source))
        assertFalse(dev.folio.runtime.source.SavedSourceReferences.available(TestFixture.source, catalog))
    }

    @Test fun malformedSavedDataStillRejectsAndOriginalSnapshotCannotBeOverwritten() = runBlocking {
        val valid = saved(); val activity = valid.activities.getValue("quiz"); val quiz = activity.data as ActivityData.Quiz
        val invalid = valid.copy(activities = valid.activities + (activity.id to activity.copy(data = quiz.copy(answers = mapOf("q1" to 9)))))
        val store = Store(invalid); val restored = session(store); val scope = CoroutineScope(coroutineContext + SupervisorJob())
        restored.attachWriter(scope)
        try {
            restored.restore(); assertNotNull(restored.storageError.value)
            val result = restored.dispatch(CommandBatch(requestId = "new", commands = listOf(WorkspaceCommand.CreateActivity(
                "new-reader", "new-view", dev.folio.model.ui.ComponentKind.READER, TestFixture.source, ActivityData.Reader()))), TestFixture.user)
            assertTrue(result.trace.error, result.trace.accepted)
            assertEquals(invalid, store.value)
            val badAnchor = valid.copy(context = valid.context.copy(source = TestFixture.source.copy(pageIndex = -1)))
            val damaged = session(Store(badAnchor)); damaged.restore(); assertNotNull(damaged.storageError.value)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun recoveryDoesNotRelaxNewSourceCreationOrOriginalPassageNavigation() = runBlocking {
        val saved = saved(); missing = true
        val restored = session(Store(saved)); restored.restore()
        val runtime = CommandExecutor(TestFixture.registry, catalog)
        assertFalse(runtime.execute(saved, CommandBatch(requestId = "source", commands = listOf(WorkspaceCommand.FocusSource(TestFixture.source))), TestFixture.ai).trace.accepted)
        val create = TestFixture.quiz("new-quiz")
        assertFalse(runtime.execute(saved, CommandBatch(requestId = "create", commands = listOf(create)), TestFixture.ai).trace.accepted)
        val pageOnly = TestFixture.source.copy(elementId = null)
        val cachedQuestion = (create.data as ActivityData.Quiz).copy(questions = (create.data as ActivityData.Quiz).questions.map { it.copy(sources = listOf(pageOnly)) })
        assertFalse(runtime.execute(saved, CommandBatch(requestId = "page-only", commands = listOf(create.copy(source = pageOnly, data = cachedQuestion))), TestFixture.ai).trace.accepted)
        val emptyReader = WorkspaceCommand.CreateActivity("empty-reader", "empty-reader-view", dev.folio.model.ui.ComponentKind.READER, pageOnly, ActivityData.Reader())
        assertTrue(runtime.execute(saved, CommandBatch(requestId = "reader-shell", commands = listOf(emptyReader)), TestFixture.ai).trace.accepted)
        assertTrue(runCatching { LearningReducer(catalog).reduce(restored.state.value,
            LearningInteraction.ConfirmReading("read", TestFixture.source, expectedReadingRevision = 1)) }.isFailure)
    }

    @Test fun absentPagePreservesRecallResponsesAndDiagramTransforms() = runBlocking {
        val base = saved()
        val created = TestFixture.execute(base,
            WorkspaceCommand.CreateActivity("recall", "recall-view", dev.folio.model.ui.ComponentKind.RECALL, TestFixture.source,
                ActivityData.Recall("Labelled prompt", "Labelled reference", listOf(TestFixture.source), response = "Private labelled response")),
            WorkspaceCommand.CreateActivity("diagram", "diagram-view", dev.folio.model.ui.ComponentKind.DIAGRAM,
                TestFixture.source.copy(elementId = "figure"), ActivityData.Diagram(zoom = 1.5f, panX = 12f)))
        assertTrue(created.trace.error, created.trace.accepted); missing = true
        val restored = session(Store(created.state)); restored.restore()
        assertNull(restored.storageError.value); assertEquals(created.state, restored.state.value)
    }

    @Test fun cachedQuestionsAndSavedAttemptsRemainNavigableWithoutSourceRetargeting() = runBlocking {
        val saved = saved(); val activity = saved.activities.getValue("quiz"); val data = activity.data as ActivityData.Quiz
        val q2 = data.questions.single().copy(id = "q2", prompt = "Second labelled cached question", sources = listOf(TestFixture.source.copy(pageIndex = 1)))
        val original = saved.copy(activities = saved.activities + (activity.id to activity.copy(data = data.copy(questions = data.questions + q2))))
        missing = true
        val restored = session(Store(original)); restored.restore()
        restored.interact(LearningInteraction.QuizQuestion("quiz", "q2"))
        assertEquals(q2.sources.single(), restored.state.value.context.source)
        val chosen = restored.state.value.activities.getValue("quiz").data as ActivityData.Quiz
        assertEquals(1, chosen.index); assertEquals(data.answers, chosen.answers)
        val history = dev.folio.runtime.practice.QuizHistoryReducer(catalog)
        val restarted = history.apply(restored.state.value, LearningInteraction.RestartQuiz("quiz", 1), CommandActor.USER)
        val reviewed = history.apply(restarted, LearningInteraction.ReviewQuizAttempt("quiz", 1, "q1"), CommandActor.USER)
        assertEquals(data.answers, (reviewed.activities.getValue("quiz").data as ActivityData.Quiz).pastAttempts.single().answers)
    }

    @Test fun providerContextMarksUnavailableReferencesWithBoundedProjection() {
        val state = saved(); missing = true
        val reader = state.activities.getValue("read").data as ActivityData.Reader
        val records = (1..205).associate { index ->
            val source = TestFixture.source.copy(elementId = "old-$index")
            val id = dev.folio.runtime.reading.ReadingConfirmations.id(source, null)
            id to ReadingConfirmation(id, source, documentChecksum = "hash", textFingerprint = "a".repeat(64), excerpt = "Labelled saved wording",
                reportedAtEpochMillis = 10, version = index.toLong())
        }
        val many = state.copy(activities = state.activities + ("read" to state.activities.getValue("read").copy(data =
            reader.copy(readingConfirmations = records, readingRevision = 205))))
        val curriculum = object : CurriculumCatalog {
            override val baseline = "fixture"; override val sourceUrl = "https://example.com"
            override fun subjects() = emptyList<dev.folio.model.library.Subject>()
            override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
        }
        val text = dev.folio.runtime.action.AppCapabilityContext(curriculum, sourceCatalog = catalog).encode(many, emptyList())
        val context = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val sources = context.getValue("unavailableSources") as kotlinx.serialization.json.JsonArray
        assertEquals(200, sources.size)
        assertTrue(context.getValue("unavailableSourceCount").toString().toInt() > 200)
        assertTrue((context.getValue("readingProgress") as kotlinx.serialization.json.JsonObject).isEmpty())
    }

    @Test fun runningTimerRecoversAtDurableTimeDespiteMissingSourceData() = runBlocking {
        val base = saved(); val activity = base.activities.getValue("quiz"); val quiz = activity.data as ActivityData.Quiz
        val timer = PracticeTimer(elapsedMillis = 1250, status = TimerStatus.RUNNING, version = 1, lastChange = TimerChange.STARTED)
        val saved = base.copy(navigation = base.navigation.copy(destination = dev.folio.model.workspace.AppDestination.WORKSPACE),
            activities = base.activities + (activity.id to activity.copy(data = quiz.copy(timer = timer))))
        missing = true; val store = Store(saved); val scope = CoroutineScope(coroutineContext + SupervisorJob()); val restored = session(store)
        restored.attachWriter(scope)
        try {
            restored.restore(); val data = restored.state.value.activities.getValue("quiz").data as ActivityData.Quiz
            assertEquals(1250L, data.timer.elapsedMillis); assertEquals(TimerChange.RECOVERED, data.timer.lastChange)
            assertEquals(TimerStatus.PAUSED, data.timer.status); assertEquals(quiz.answers, data.answers)
            assertEquals(restored.state.value, store.value)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
