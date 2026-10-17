package dev.folio.model.activity

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable enum class ObservationKind { CHECKED_CORRECT, CHECKED_INCORRECT, RECALL_REPORT }

/** Read-only projection of saved learning records; no private response or inferred mastery. */
@Serializable
data class LearningObservation(val id: String, val activityId: String, val questionId: String?,
    val source: SourceAnchor, val prompt: String, val kind: ObservationKind,
    val confidence: AnswerConfidence? = null, val recallRating: RecallRating? = null,
    val attemptNumber: Int? = null, val reportedAtEpochMillis: Long? = null, val reportedByAssistant: Boolean = false,
    val checkByAssistant: Boolean? = null, val selectionByAssistant: Boolean? = null)

@Serializable
data class LearningSummary(val checked: Int, val correct: Int, val incorrect: Int,
    val confidentMistakes: Int, val uncertainCorrect: Int, val recallReports: Int)
