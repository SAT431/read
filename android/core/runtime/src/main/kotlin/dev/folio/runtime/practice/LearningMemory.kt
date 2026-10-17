package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.workspace.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Observations stay separate from suggestions/hypotheses. Missing confidence never means LOW. */
object LearningMemory {
    fun observations(state: Workspace): List<LearningObservation> = state.activities.values.sortedBy { it.id }.flatMap { activity ->
        when (val data = activity.data) {
            is ActivityData.Quiz -> data.pastAttempts.flatMap { saved -> quizObservations(activity.id, saved.number,
                saved.questions, saved.answers, saved.submitted, saved.confidence, saved.checks) } +
                quizObservations(activity.id, data.attemptNumber, data.questions, data.answers, data.submitted, data.confidence, data.checks)
            is ActivityData.Recall -> data.sources.firstOrNull()?.let { source ->
                val past = data.pastAttempts.mapIndexed { index, attempt -> LearningObservation("", activity.id, null,
                    source, data.prompt, ObservationKind.RECALL_REPORT, recallRating = attempt.rating, attemptNumber = index + 1,
                    reportedAtEpochMillis = attempt.ratedAtEpochMillis, reportedByAssistant = attempt.ratingByAssistant) }
                if (data.revealed && data.rating != null && data.ratedAtEpochMillis != null) past + LearningObservation("", activity.id, null,
                    source, data.prompt, ObservationKind.RECALL_REPORT, recallRating = data.rating, attemptNumber = past.size + 1,
                    reportedAtEpochMillis = data.ratedAtEpochMillis, reportedByAssistant = data.ratingByAssistant) else past
            }.orEmpty()
            else -> emptyList()
        }
    }.map { observation ->
        val bytes = Json.encodeToString(LearningObservation.serializer(), observation).toByteArray(Charsets.UTF_8)
        observation.copy(id = "learning-" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }
    private fun quizObservations(activityId: String, number: Int, questions: List<Question>, answers: Map<String, Int>,
        submitted: Set<String>, confidence: Map<String, AnswerConfidence>, checks: Map<String, QuizCheck>): List<LearningObservation> =
        questions.filter { it.id in submitted && it.id in answers }.mapNotNull { q -> q.sources.firstOrNull()?.let { source ->
            LearningObservation("", activityId, q.id, source, q.prompt,
                if (answers[q.id] == q.correctOption) ObservationKind.CHECKED_CORRECT else ObservationKind.CHECKED_INCORRECT,
                confidence = confidence[q.id], attemptNumber = number, reportedAtEpochMillis = checks[q.id]?.checkedAtEpochMillis,
                checkByAssistant = checks[q.id]?.byAssistant, selectionByAssistant = checks[q.id]?.selectionByAssistant)
        } }
    fun matches(observation: LearningObservation, filter: LearningFilter): Boolean = when (filter) {
        LearningFilter.ALL -> true
        LearningFilter.MISTAKES -> observation.kind == ObservationKind.CHECKED_INCORRECT
        LearningFilter.CONFIDENT_MISTAKES -> observation.kind == ObservationKind.CHECKED_INCORRECT && observation.confidence?.level == ConfidenceLevel.HIGH
        LearningFilter.UNCERTAIN_CORRECT -> observation.kind == ObservationKind.CHECKED_CORRECT && observation.confidence?.level == ConfidenceLevel.LOW
        LearningFilter.RECALL_REPORTS -> observation.kind == ObservationKind.RECALL_REPORT
    }
    fun summary(observations: List<LearningObservation>) = LearningSummary(
        checked = observations.count { it.kind != ObservationKind.RECALL_REPORT },
        correct = observations.count { it.kind == ObservationKind.CHECKED_CORRECT },
        incorrect = observations.count { it.kind == ObservationKind.CHECKED_INCORRECT },
        confidentMistakes = observations.count { matches(it, LearningFilter.CONFIDENT_MISTAKES) },
        uncertainCorrect = observations.count { matches(it, LearningFilter.UNCERTAIN_CORRECT) },
        recallReports = observations.count { it.kind == ObservationKind.RECALL_REPORT })
}
