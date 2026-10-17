package dev.folio.runtime.practice

import dev.folio.model.activity.ActivityData

/** Only explicit submissions count. A selected option or page visit is not a completed attempt. */
data class QuizProgress(val total: Int, val checked: Int, val correct: Int, val mistakes: List<Int>, val unchecked: List<Int>) {
    companion object {
        fun from(quiz: ActivityData.Quiz): QuizProgress {
            val mistakes = quiz.questions.indices.filter { index -> quiz.questions[index].let {
                it.id in quiz.submitted && quiz.answers[it.id] != it.correctOption
            } }
            val unchecked = quiz.questions.indices.filter { quiz.questions[it].id !in quiz.submitted }
            val checked = quiz.questions.size - unchecked.size
            return QuizProgress(quiz.questions.size, checked, checked - mistakes.size, mistakes, unchecked)
        }
    }
    fun nextAfter(index: Int, candidates: List<Int>): Int? = candidates.firstOrNull { it > index } ?: candidates.firstOrNull()
}
