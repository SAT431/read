package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RevisionSuggestionTest {
    private val source = TestFixture.source
    private val question = Question("q", "Question", listOf("A", "B", "C", "D"), 0, "Reference", listOf(source))
    private fun quiz(checked: Boolean, answer: Int = 0) = Activity("quiz", source,
        ActivityData.Quiz(listOf(question), answers = mapOf("q" to answer), submitted = if (checked) setOf("q") else emptySet()))
    private fun recall(rating: RecallRating? = null, previous: List<RecallRating> = emptyList(), assistant: Boolean = false) = Activity("recall", source,
        ActivityData.Recall("Prompt", "Reference", listOf(source), response = "Private response", revealed = rating != null,
            rating = rating, ratedAtEpochMillis = rating?.let { 100L }, ratingByAssistant = assistant,
            pastAttempts = previous.mapIndexed { i, value -> RecallAttempt("Private past response", value, i.toLong(), true, assistant) }))
    private fun suggest(activity: Activity, questionId: String? = null) = RevisionSuggestions.forActivity(activity, questionId)
    private val reducer = RevisionQueueReducer(TestFixture.catalog) { 1000L }
    private fun state(activity: Activity) = Workspace(activities = mapOf(activity.id to activity))

    @Test fun onlyCheckedQuizAnswersProvideSuggestionsWithoutInventingPracticeHistory() {
        assertNull(suggest(quiz(false), "q")); assertNull(suggest(quiz(true), "missing"))
        val correct = suggest(quiz(true), "q")!!
        assertEquals(3, correct.delayDays); assertEquals(RevisionReason.QUIZ_CORRECT, correct.reason); assertEquals(1, correct.observations)
        val incorrect = suggest(quiz(true, 2), "q")!!
        assertEquals(1, incorrect.delayDays); assertEquals(RevisionReason.QUIZ_INCORRECT, incorrect.reason)
        assertNotEquals(correct.evidenceFingerprint, incorrect.evidenceFingerprint)
    }
    @Test fun revealWithoutRatingAndUnfinishedAttemptsDoNotProduceRecallAdvice() {
        assertNull(suggest(recall()))
        val previous = recall(previous = listOf(RecallRating.RECALLED))
        assertNull(suggest(previous))
        assertNull(suggest(previous.copy(data = (previous.data as ActivityData.Recall).copy(revealed = true))))
        assertNull(suggest(recall(RecallRating.RECALLED), "q"))
        assertNull(suggest(TestFixture.reader().activities.values.first()))
    }
    @Test fun reportedRecallRatingsAndConsecutiveSuccessesControlIntervalsWithACap() {
        assertEquals(1, suggest(recall(RecallRating.FORGOT))!!.delayDays)
        assertEquals(3, suggest(recall(RecallRating.PARTIAL))!!.delayDays)
        for ((past, expected) in listOf(0 to 7, 1 to 14, 2 to 30, 3 to 60, 4 to 90, 99 to 90)) {
            val advice = suggest(recall(RecallRating.RECALLED, List(past) { RecallRating.RECALLED }))!!
            assertEquals(expected, advice.delayDays); assertEquals(past + 1, advice.recalledStreak)
            assertEquals(past + 1, advice.observations)
        }
        val reset = suggest(recall(RecallRating.RECALLED, listOf(RecallRating.RECALLED, RecallRating.PARTIAL)))!!
        assertEquals(7, reset.delayDays); assertEquals(1, reset.recalledStreak)
    }
    @Test fun privateResponseContentIsExcludedFromSuggestionAndFingerprint() {
        val original = recall(RecallRating.RECALLED, listOf(RecallRating.PARTIAL))
        val data = original.data as ActivityData.Recall
        val edited = original.copy(data = data.copy(response = "Different private response", pastAttempts = data.pastAttempts.map { it.copy(response = "Different past response") }))
        assertEquals(suggest(original), suggest(edited))
        assertFalse(Json.encodeToJsonElement(suggest(original)!!).toString().contains("Private"))
    }
    @Test fun ratingProvenanceAndReferenceChangesRemainVisibleInEvidence() {
        val byUser = recall(RecallRating.PARTIAL)
        val byAssistant = recall(RecallRating.PARTIAL, assistant = true)
        assertTrue(suggest(byAssistant)!!.includesAssistantRatings)
        assertNotEquals(suggest(byUser)!!.evidenceFingerprint, suggest(byAssistant)!!.evidenceFingerprint)
        val changed = byUser.copy(data = (byUser.data as ActivityData.Recall).copy(referenceAnswer = "Updated reference"))
        assertNotEquals(suggest(byUser)!!.evidenceFingerprint, suggest(changed)!!.evidenceFingerprint)
    }
    @Test fun acceptingNativeSuggestionSchedulesWithoutChangingAnswerAndManualChoiceClearsSuggestion() {
        val activity = recall(RecallRating.RECALLED, listOf(RecallRating.RECALLED))
        val advice = suggest(activity)!!; val before = state(activity)
        val accepted = reducer.apply(before, AppCommand.AcceptRevisionSuggestion(activity.id, null, advice.evidenceFingerprint), CommandActor.AI)
        val item = accepted.revisionItems.values.single()
        assertEquals(1000 + 14 * 86_400_000L, item.dueAtEpochMillis); assertEquals(advice, item.suggestion)
        assertTrue(item.scheduledByAssistant); assertEquals(before.activities, accepted.activities)
        val manual = reducer.apply(accepted, AppCommand.ScheduleRevision(activity.id, null, 1), CommandActor.USER)
        assertNull(manual.revisionItems.values.single().suggestion)
        assertEquals(before.activities, manual.activities)
    }
    @Test fun staleMissingAndFabricatedEvidenceCannotBeAccepted() {
        val activity = recall(RecallRating.PARTIAL); val advice = suggest(activity)!!
        val changed = activity.copy(data = (activity.data as ActivityData.Recall).copy(rating = RecallRating.FORGOT))
        for (before in listOf(state(changed), state(recall()), Workspace()))
            assertTrue(runCatching { reducer.apply(before, AppCommand.AcceptRevisionSuggestion("recall", null, advice.evidenceFingerprint), CommandActor.USER) }.isFailure)
        assertTrue(runCatching { reducer.apply(state(activity), AppCommand.AcceptRevisionSuggestion("recall", null, "made-up"), CommandActor.USER) }.isFailure)
    }
    @Test fun reviewCompletionWithoutNewRetrievalDoesNotIncreaseTheSuggestedInterval() {
        val activity = recall(RecallRating.RECALLED); val advice = suggest(activity)!!
        val scheduled = reducer.apply(state(activity), AppCommand.AcceptRevisionSuggestion(activity.id, null, advice.evidenceFingerprint), CommandActor.USER)
        val item = scheduled.revisionItems.values.single()
        val done = reducer.apply(scheduled, AppCommand.CompleteRevision(item.id, item.version), CommandActor.USER)
        assertEquals(advice, suggest(done.activities.getValue(activity.id)))
        assertTrue(done.revisionItems.values.single().done)
    }
    @Test fun wireAndInteractionBridgeUseSameNativeCommand() {
        val command = AppCommand.AcceptRevisionSuggestion("recall", null, "f".repeat(64))
        val batch = AppCommandBatch(requestId = "suggestion", commands = listOf(command))
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        assertEquals(command, LearningCommandBridge.command(LearningCommandBridge.event(command)!!))
        assertEquals("revision.accept_suggestion", AppActionRegistry.id(command))
    }
    @Test fun sharedAppExecutorRejectsStaleSuggestionAndRollsBackTheWholeBatch() {
        val curriculum = object : CurriculumCatalog {
            override val baseline = "fixture"; override val sourceUrl = "https://example.com"
            override fun subjects() = emptyList<dev.folio.model.library.Subject>()
            override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
        }
        val library = object : PdfLibrary {
            override val entries = kotlinx.coroutines.flow.MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val activity = recall(RecallRating.PARTIAL); val before = state(activity)
        val advice = suggest(activity)!!
        val accept = AppCommand.AcceptRevisionSuggestion(activity.id, null, advice.evidenceFingerprint)
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
        val batch = AppCommandBatch(requestId = "accept", expectedRevision = before.revision, commands = listOf(accept))
        val valid = executor.execute(before, batch, TestFixture.ai)
        assertTrue(valid.trace.error, valid.trace.accepted)
        assertEquals(advice, valid.state.revisionItems.values.single().suggestion)
        val invalid = executor.execute(before, batch.copy(commands = listOf(accept, AppCommand.OpenRevision("unknown"))), TestFixture.ai)
        assertFalse(invalid.trace.accepted); assertEquals(before, invalid.state); assertTrue(invalid.effects.isEmpty())
        val locked = executor.execute(before, batch, ExecutionPolicy(CommandActor.AI, interactionLocked = true))
        assertFalse(locked.trace.accepted); assertEquals(before, locked.state)
        val changed = before.copy(activities = mapOf(activity.id to recall(RecallRating.FORGOT)))
        assertFalse(executor.execute(changed, batch, TestFixture.ai).trace.accepted)
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum, sourceCatalog = TestFixture.catalog).encode(before, emptyList())).jsonObject
        val exported = context.getValue("revisionSuggestions").jsonArray.single().jsonObject
        assertEquals(advice.evidenceFingerprint, exported.getValue("evidenceFingerprint").jsonPrimitive.content)
        assertFalse(exported.toString().contains("Private response"))
    }
    @Test fun boundedProviderContextPrioritisesFocusAndExcludesUnavailableSources() {
        val activities = (0..205).associate { i -> recall(RecallRating.RECALLED).copy(id = "card-$i").let { it.id to it } }
        val state = Workspace(activities = activities, context = StudyContext(focusedActivityId = "card-205"))
        val suggestions = RevisionSuggestions.forWorkspace(state, TestFixture.catalog)
        assertEquals(200, suggestions.size); assertEquals("card-205", suggestions.first().activityId)
        assertTrue(RevisionSuggestions.forWorkspace(state, null).isEmpty())
        val missing = recall(RecallRating.PARTIAL).let { it.copy(data = (it.data as ActivityData.Recall).copy(sources = listOf(source.copy(documentId = "missing")))) }
        assertTrue(RevisionSuggestions.forWorkspace(state(missing), TestFixture.catalog).isEmpty())
    }
}
