package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable enum class RevisionReason { QUIZ_INCORRECT, QUIZ_CORRECT, RECALL_FORGOT, RECALL_PARTIAL, RECALL_RECALLED }

/** A transparent scheduling heuristic from explicit observations; no mastery/retention estimate. */
@Serializable
data class RevisionSuggestion(val activityId: String, val questionId: String?, val delayDays: Int,
    val reason: RevisionReason, val evidenceFingerprint: String, val observations: Int,
    val recalledStreak: Int = 0, val includesAssistantRatings: Boolean = false, val policyVersion: Int = 1, val quizAttemptNumber: Int? = null)
