package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.practice.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LearningMemoryTest {
    private val questions = (0..3).map { index -> Question("q$index", "Question $index", listOf("A", "B", "C", "D"), 0,
        "Reference", listOf(TestFixture.source.copy(pageIndex = index))) }
    private val quiz = ActivityData.Quiz(questions, answers = mapOf("q0" to 1, "q1" to 0, "q2" to 0, "q3" to 1), submitted = setOf("q0", "q1", "q2"),
        confidence = mapOf("q0" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 10), "q1" to AnswerConfidence(ConfidenceLevel.LOW, 0, 11, true)))
    private val recall = ActivityData.Recall("Recall prompt", "Reference", listOf(TestFixture.source), response = "PRIVATE_CURRENT_RESPONSE", revealed = true,
        rating = RecallRating.RECALLED, ratedAtEpochMillis = 20, pastAttempts = listOf(RecallAttempt("PRIVATE_PAST_RESPONSE", RecallRating.FORGOT, 12, true, true)))
    private fun initial() = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ,
        TestFixture.source, quiz), WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, TestFixture.source, recall)).state
    @Test fun onlyCheckedAnswersAndExplicitReportsBecomeObservationsWithUnknownConfidenceKeptUnknown() {
        val observations = LearningMemory.observations(initial())
        val summary = LearningMemory.summary(observations)
        assertEquals(LearningSummary(3, 2, 1, 1, 1, 2), summary)
        assertFalse(observations.any { it.questionId == "q3" })
        assertNull(observations.single { it.questionId == "q2" }.confidence)
        assertTrue(observations.filter { it.questionId != null }.all { it.reportedAtEpochMillis == null })
        assertEquals(1, observations.count { LearningMemory.matches(it, LearningFilter.CONFIDENT_MISTAKES) })
        assertEquals(1, observations.count { LearningMemory.matches(it, LearningFilter.UNCERTAIN_CORRECT) })
        assertEquals(2, observations.count { LearningMemory.matches(it, LearningFilter.RECALL_REPORTS) })
    }
    @Test fun projectionExcludesPrivateResponsesAndDoesNotTurnAnUnratedAttemptIntoAReport() {
        val before = initial(); val observations = LearningMemory.observations(before)
        val json = Json.encodeToJsonElement(observations).toString()
        assertFalse(json.contains("PRIVATE_CURRENT_RESPONSE") || json.contains("PRIVATE_PAST_RESPONSE"))
        assertTrue(observations.single { it.questionId == null && it.attemptNumber == 1 }.reportedByAssistant)
        val activity = before.activities.getValue("recall")
        val changed = before.copy(activities = before.activities + (activity.id to activity.copy(data = recall.copy(revealed = false, rating = null, ratedAtEpochMillis = null))))
        assertEquals(1, LearningMemory.observations(changed).count { it.kind == ObservationKind.RECALL_REPORT })
    }
    @Test fun stableObservationIdsIgnoreLayoutAndPrivateTextButChangeWithTheirSourceEvidence() {
        val before = initial(); val observations = LearningMemory.observations(before)
        val moved = before.copy(components = before.components.mapValues { it.value.copy(visible = false) })
        assertEquals(observations, LearningMemory.observations(moved))
        val activity = before.activities.getValue("recall")
        val edited = before.copy(activities = before.activities + (activity.id to activity.copy(data = recall.copy(response = "OTHER_PRIVATE_RESPONSE"))))
        assertEquals(observations, LearningMemory.observations(edited))
        val q = before.activities.getValue("quiz")
        val changedQuestions = questions.map { it.copy(prompt = "Changed ${it.id}") }
        val altered = before.copy(activities = before.activities + (q.id to q.copy(data = quiz.copy(questions = changedQuestions))))
        assertNotEquals(observations.filter { it.questionId != null }.map { it.id }, LearningMemory.observations(altered).filter { it.questionId != null }.map { it.id })
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
    private fun apply(state: Workspace, vararg commands: AppCommand, policy: ExecutionPolicy = TestFixture.user) = executor.execute(state,
        AppCommandBatch(requestId = "memory", expectedRevision = state.revision, commands = commands.toList()), policy)
    @Test fun openingExactSavedQuestionRestoresSourceAndPreservesConfidenceAnswersAndReader() {
        val before = initial(); val observation = LearningMemory.observations(before).single { it.questionId == "q1" }
        val paused = TestFixture.execute(before, WorkspaceCommand.SuspendActivity("quiz")).state
        val result = apply(paused, AppCommand.OpenLearningObservation(observation.id))
        assertTrue(result.trace.error, result.trace.accepted)
        val data = result.state.activities.getValue("quiz").data as ActivityData.Quiz
        assertEquals(1, data.index); assertEquals(quiz.confidence, data.confidence); assertEquals(quiz.answers, data.answers)
        assertEquals(quiz.submitted, data.submitted); assertEquals(questions[1].sources.first(), result.state.context.source)
        assertEquals(before.activities.getValue("read"), result.state.activities.getValue("read"))
        assertNull(result.state.navigation.pdfId); assertEquals(AppDestination.WORKSPACE, result.state.navigation.destination)
    }
    @Test fun historicalRecallOpeningResumesCurrentCardWithoutRestoringPastResponseOrCompletingReview() {
        val before = initial(); val old = LearningMemory.observations(before).single { it.questionId == null && it.attemptNumber == 1 }
        val opened = apply(before, AppCommand.OpenLearningObservation(old.id))
        assertTrue(opened.trace.accepted)
        assertEquals(recall, opened.state.activities.getValue("recall").data)
        assertTrue(opened.state.revisionItems.isEmpty())
    }
    @Test fun unknownOrProtectedObservationRejectsAtomicActionsAndEffects() {
        val before = initial(); val old = LearningMemory.observations(before).single { it.questionId == "q0" }
        val invalid = apply(before, AppCommand.SetLearningFilter(LearningFilter.MISTAKES), AppCommand.OpenLearningObservation("missing"))
        assertFalse(invalid.trace.accepted); assertEquals(before, invalid.state); assertTrue(invalid.effects.isEmpty())
        val pinned = before.copy(components = before.components + ("quiz-view" to before.components.getValue("quiz-view").copy(pinned = true, visible = false)))
        assertFalse(apply(pinned, AppCommand.OpenLearningObservation(old.id), policy = TestFixture.ai).trace.accepted)
    }
    @Test fun filtersWireAndProviderContextExposeActualFactsAndAnExplicitBound() {
        val before = initial()
        val batch = AppCommandBatch(requestId = "filter", commands = listOf(AppCommand.Navigate(AppDestination.LEARNING), AppCommand.SetLearningFilter(LearningFilter.CONFIDENT_MISTAKES)))
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        val filtered = executor.execute(before, batch, TestFixture.ai)
        assertTrue(filtered.trace.accepted); assertEquals(LearningFilter.CONFIDENT_MISTAKES, filtered.state.navigation.learningFilter)
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(before, emptyList())).jsonObject
        assertEquals(5, context.getValue("learningObservations").jsonArray.size)
        assertFalse(context.getValue("learningObservationsTruncated").jsonPrimitive.boolean)
        assertEquals(1, context.getValue("learningSummary").jsonObject.getValue("confidentMistakes").jsonPrimitive.int)
        val many = before.copy(activities = (0..210).associate { index -> before.activities.getValue("quiz").copy(id = "quiz-$index").let { it.id to it } },
            context = before.context.copy(focusedActivityId = "quiz-210"))
        val bounded = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(many, emptyList())).jsonObject
        assertEquals(200, bounded.getValue("learningObservations").jsonArray.size)
        assertTrue(bounded.getValue("learningObservationsTruncated").jsonPrimitive.boolean)
        assertEquals("quiz-210", bounded.getValue("learningObservations").jsonArray.first().jsonObject.getValue("activityId").jsonPrimitive.content)
        assertEquals(633, bounded.getValue("learningSummary").jsonObject.getValue("checked").jsonPrimitive.int)
    }
}
