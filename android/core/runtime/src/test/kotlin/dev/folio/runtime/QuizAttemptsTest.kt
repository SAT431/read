package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.*
import dev.folio.runtime.validation.ActivityValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class QuizAttemptsTest {
    private var time = 10L
    private val learning = LearningReducer(TestFixture.catalog) { time }
    private val history = QuizHistoryReducer(TestFixture.catalog) { time }
    private fun initial() = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
    private fun quiz(state: Workspace) = state.activities.getValue("quiz").data as ActivityData.Quiz
    private fun choose(state: Workspace = initial(), option: Int = 1, actor: CommandActor = CommandActor.USER) =
        learning.reduce(state, LearningInteraction.SelectAnswer("quiz", "q1", option), actor)
    private fun check(state: Workspace, actor: CommandActor = CommandActor.USER) = learning.reduce(state, LearningInteraction.SubmitAnswer("quiz", "q1"), actor)
    private fun restart(state: Workspace, expected: Int = quiz(state).attemptNumber, actor: CommandActor = CommandActor.USER) =
        history.apply(state, LearningInteraction.RestartQuiz("quiz", expected), actor)
    private fun review(state: Workspace, number: Int? = 1) = history.apply(state, LearningInteraction.ReviewQuizAttempt("quiz", number, null), CommandActor.USER)

    @Test fun partialAttemptPreservesChoicesAndConfidenceWithoutCheckingOrInventingResults() {
        val selected = choose()
        val reported = QuizConfidenceReducer { 11L }.apply(selected, LearningInteraction.SetAnswerConfidence("quiz", "q1", 1, null, ConfidenceLevel.HIGH), CommandActor.USER)
        time = 20
        val next = restart(reported, actor = CommandActor.AI)
        val saved = quiz(next).pastAttempts.single()
        assertEquals(quiz(reported).questions, saved.questions); assertEquals(mapOf("q1" to 1), saved.answers)
        assertEquals(quiz(reported).confidence, saved.confidence); assertTrue(saved.submitted.isEmpty() && saved.checks.isEmpty())
        assertEquals(20L, saved.archivedAtEpochMillis); assertTrue(saved.archivedByAssistant)
        assertEquals(2, quiz(next).attemptNumber)
        assertTrue(quiz(next).answers.isEmpty() && quiz(next).confidence.isEmpty() && quiz(next).checks.isEmpty())
        assertEquals(0, LearningMemory.summary(LearningMemory.observations(next)).checked)
    }
    @Test fun nativeCheckTimeAndSeparateSelectionCheckActorsArePreserved() {
        val selected = choose(actor = CommandActor.AI)
        val checked = check(selected)
        assertEquals(QuizCheck(10, false, true), quiz(checked).checks.getValue("q1"))
        val next = restart(checked)
        assertEquals(quiz(checked).checks, quiz(next).pastAttempts.single().checks)
        val observation = LearningMemory.observations(next).single()
        assertEquals(10L, observation.reportedAtEpochMillis); assertEquals(false, observation.checkByAssistant)
        assertEquals(true, observation.selectionByAssistant); assertEquals(1, observation.attemptNumber)
    }
    @Test fun observationIdentityStaysStableAcrossArchivingAndRepeatedResultsRemainDistinct() {
        val checked = check(choose()); val first = LearningMemory.observations(checked).single()
        val next = restart(checked)
        assertEquals(first, LearningMemory.observations(next).single())
        time = 30
        val second = check(choose(next, 0))
        val observations = LearningMemory.observations(second)
        assertEquals(2, observations.size); assertEquals(2, observations.map { it.id }.distinct().size)
        assertEquals(first, observations.single { it.attemptNumber == 1 })
        assertEquals(LearningSummary(2, 1, 1, 0, 0, 0), LearningMemory.summary(observations))
    }
    @Test fun historicalReviewIsReadOnlyAndReturningKeepsCurrentDraftUntouched() {
        val next = choose(restart(check(choose())), option = 0)
        val current = quiz(next)
        val viewed = review(next)
        assertEquals(QuizHistorySelection(1, "q1"), quiz(viewed).review)
        assertEquals(current.answers, quiz(viewed).answers)
        for (event in listOf(LearningInteraction.SelectAnswer("quiz", "q1", 2), LearningInteraction.SubmitAnswer("quiz", "q1"),
            LearningInteraction.SetAnswerConfidence("quiz", "q1", 0, null, ConfidenceLevel.LOW), LearningInteraction.RestartQuiz("quiz", 2)))
            assertTrue(runCatching { learning.reduce(viewed, event) }.isFailure)
        val returned = review(viewed, null)
        assertEquals(current, quiz(returned))
    }
    @Test fun oldQuestionKeyAndSourceRemainAvailableWhenCurrentQuestionsChange() {
        val checked = check(choose()); val observation = LearningMemory.observations(checked).single()
        val next = restart(checked); val activity = next.activities.getValue("quiz")
        val altered = quiz(next).copy(questions = quiz(next).questions.map { it.copy(prompt = "New prompt", correctOption = 2, sources = listOf(TestFixture.source.copy(pageIndex = 2))) })
        val changed = next.copy(activities = next.activities + (activity.id to activity.copy(data = altered)))
        val opened = executor.execute(changed, AppCommandBatch(requestId = "old", commands = listOf(AppCommand.OpenLearningObservation(observation.id))), TestFixture.user)
        assertTrue(opened.trace.error, opened.trace.accepted)
        assertEquals(QuizHistorySelection(1, "q1"), quiz(opened.state).review)
        assertEquals(TestFixture.source, opened.state.context.source)
        assertEquals("Question", quiz(opened.state).pastAttempts.single().questions.single().prompt)
        assertEquals(0, quiz(opened.state).pastAttempts.single().questions.single().correctOption)
        assertEquals(altered.answers, quiz(opened.state).answers)
        val returned = review(opened.state, null)
        assertEquals(TestFixture.source.copy(pageIndex = 2), returned.context.source)
    }
    @Test fun legacyCheckedAnswersKeepUnknownTimesAndSelectionAuthorshipUnknown() {
        val initial = initial(); val activity = initial.activities.getValue("quiz")
        val legacy = initial.copy(activities = initial.activities + (activity.id to activity.copy(data = quiz(initial).copy(answers = mapOf("q1" to 0), submitted = setOf("q1")))))
        val saved = restart(legacy)
        val observation = LearningMemory.observations(saved).single()
        assertNull(observation.reportedAtEpochMillis); assertNull(observation.selectionByAssistant); assertNull(observation.checkByAssistant)
        val prefetched = check(choose().let { selected -> selected.copy(activities = selected.activities.mapValues { (_, a) ->
            if (a.id == "quiz") a.copy(data = (a.data as ActivityData.Quiz).copy(selectionActors = emptyMap())) else a }) })
        assertNull(quiz(prefetched).checks.getValue("q1").selectionByAssistant)
    }
    @Test fun duplicateStaleEmptyHiddenAndHistoryLimitRestartsRejectWithoutDeletingWork() {
        assertTrue(runCatching { restart(initial()) }.isFailure)
        val checked = check(choose()); val next = restart(checked)
        assertTrue(runCatching { restart(next, 1) }.isFailure)
        val hidden = TestFixture.execute(checked, WorkspaceCommand.SuspendActivity("quiz")).state
        assertTrue(runCatching { restart(hidden) }.isFailure)
        val saved = quiz(next).pastAttempts.single()
        val full = quiz(checked).copy(pastAttempts = List(100) { saved.copy(number = it + 1) }, attemptNumber = 101)
        val limited = checked.copy(activities = checked.activities + ("quiz" to checked.activities.getValue("quiz").copy(data = full)))
        assertTrue(runCatching { restart(limited) }.isFailure)
        assertEquals(100, quiz(limited).pastAttempts.size)
    }
    @Test fun malformedHistoryAndAiCreationWithInjectedArchivesAreRejected() {
        val next = restart(check(choose())); val data = quiz(next)
        val create = (TestFixture.quiz() as WorkspaceCommand.CreateActivity).copy(data = data)
        assertFalse(TestFixture.execute(TestFixture.reader(), create, policy = TestFixture.ai).trace.accepted)
        for (bad in listOf(data.copy(attemptNumber = 1), data.copy(pastAttempts = listOf(data.pastAttempts.single().copy(number = 2))),
            data.copy(review = QuizHistorySelection(99, "q1")), data.copy(checks = mapOf("q1" to QuizCheck(-1))),
            data.copy(pastAttempts = listOf(data.pastAttempts.single().copy(checks = mapOf("q1" to QuizCheck(10, false, true)))))))
            assertTrue(runCatching { ActivityValidator.validate(bad, TestFixture.source, TestFixture.catalog) }.isFailure)
    }
    @Test fun unavailableHistoricalSourceKeepsCachedReviewButCannotOpenOriginalPassage() {
        val next = restart(check(choose())); val current = quiz(next); val saved = current.pastAttempts.single()
        val unavailable = saved.copy(questions = saved.questions.map { it.copy(sources = listOf(TestFixture.source.copy(documentId = "missing"))) })
        val data = current.copy(pastAttempts = listOf(unavailable))
        ActivityValidator.validate(data, TestFixture.source, TestFixture.catalog)
        val state = next.copy(activities = next.activities + ("quiz" to next.activities.getValue("quiz").copy(data = data)))
        val viewed = review(state)
        assertEquals(QuizHistorySelection(1, "q1"), quiz(viewed).review)
        assertEquals(unavailable, quiz(viewed).pastAttempts.single())
        assertFalse(TestFixture.execute(viewed, WorkspaceCommand.FocusSource(unavailable.questions.single().sources.single())).trace.accepted)
        assertEquals("missing", LearningMemory.observations(state).single().source.documentId)
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    @Test fun wireBridgeAndAtomicRestartBatchesPreserveLearningOnRejectedSecondRestart() {
        val checked = check(choose())
        val restart = AppCommand.RestartQuiz("quiz", 1)
        val review = AppCommand.ReviewQuizAttempt("quiz", 1, null)
        val batch = AppCommandBatch(requestId = "attempt", commands = listOf(restart, review))
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        for (command in batch.commands) assertEquals(command, LearningCommandBridge.command(LearningCommandBridge.event(command)!!))
        val valid = executor.execute(checked, batch, TestFixture.user)
        assertTrue(valid.trace.error, valid.trace.accepted); assertEquals(1, quiz(valid.state).review!!.attemptNumber)
        val rejected = executor.execute(checked, batch.copy(commands = listOf(restart, restart)), TestFixture.user)
        assertFalse(rejected.trace.accepted); assertEquals(checked, rejected.state)
    }
    @Test fun boundedProviderProjectionKeepsViewedArchiveAndFullObservationCounts() {
        val next = restart(check(choose())); val saved = quiz(next).pastAttempts.single()
        val all = quiz(next).copy(pastAttempts = (1..6).map { saved.copy(number = it) }, attemptNumber = 7)
        val before = next.copy(activities = next.activities + ("quiz" to next.activities.getValue("quiz").copy(data = all)))
        val viewed = review(before)
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(viewed, emptyList())).jsonObject
        val projected = context.getValue("activities").jsonObject.getValue("quiz").jsonObject.getValue("data").jsonObject
        assertEquals(listOf(1, 4, 5, 6), projected.getValue("pastAttempts").jsonArray.map { it.jsonObject.getValue("number").jsonPrimitive.int })
        assertEquals(2, context.getValue("quizHistoryOmitted").jsonObject.getValue("quiz").jsonPrimitive.int)
        assertEquals(6, context.getValue("learningSummary").jsonObject.getValue("checked").jsonPrimitive.int)
        assertEquals(6, quiz(viewed).pastAttempts.size)
    }
    @Test fun newAttemptInvalidatesOldSuggestionEvidenceAndReviewDoesNotPromoteAnInterval() {
        val checked = check(choose(option = 0)); val first = RevisionSuggestions.forActivity(checked.activities.getValue("quiz"), "q1")!!
        val next = restart(checked); time = 30
        val again = check(choose(next, 0)); val second = RevisionSuggestions.forActivity(again.activities.getValue("quiz"), "q1")!!
        assertNotEquals(first.evidenceFingerprint, second.evidenceFingerprint)
        assertEquals(1, first.quizAttemptNumber); assertEquals(2, second.quizAttemptNumber)
        assertEquals(first.delayDays, second.delayDays)
        assertNull(RevisionSuggestions.forActivity(review(again).activities.getValue("quiz"), "q1"))
    }
}
