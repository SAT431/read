package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable data class QuizCheck(val checkedAtEpochMillis: Long, val byAssistant: Boolean = false,
    val selectionByAssistant: Boolean? = null)
@Serializable data class QuizHistorySelection(val attemptNumber: Int, val questionId: String)

/** Immutable saved questions/keys and learner work, including unchecked partial choices. */
@Serializable
data class QuizAttempt(val number: Int, val questions: List<Question>, val index: Int,
    val answers: Map<String, Int>, val submitted: Set<String>, val confidence: Map<String, AnswerConfidence>,
    val checks: Map<String, QuizCheck>, val selectionActors: Map<String, dev.folio.model.command.CommandActor>,
    val archivedAtEpochMillis: Long, val archivedByAssistant: Boolean = false, val timer: PracticeTimer? = null)
