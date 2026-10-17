package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.validation.QuizDataValidator

class QuizHistoryReducer(private val catalog: SourceCatalog?, private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val id = when (event) {
            is LearningInteraction.RestartQuiz -> event.activityId
            is LearningInteraction.ReviewQuizAttempt -> event.activityId
            else -> error("Not a quiz history action")
        }
        val activity = state.activities[id] ?: error("Unknown quiz")
        val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any { it.activityId == id && it.visible }) { "Show and resume the quiz first" }
        val updated = when (event) {
            is LearningInteraction.RestartQuiz -> {
                require(quiz.review == null && quiz.attemptNumber == event.expectedAttempt) { "Quiz attempt changed; return to the current attempt" }
                require(quiz.answers.isNotEmpty()) { "The current attempt has no work to save" }
                require(quiz.pastAttempts.size < 100) { "Quiz attempt history limit reached" }
                val time = now(); require(time >= 0)
                val saved = QuizAttempt(quiz.attemptNumber, quiz.questions, quiz.index, quiz.answers, quiz.submitted, quiz.confidence,
                    quiz.checks, quiz.selectionActors, time, actor == CommandActor.AI,
                    PracticeTimerRules.pause(quiz.timer, TimerChange.ARCHIVED))
                quiz.copy(index = 0, answers = emptyMap(), submitted = emptySet(), confidence = emptyMap(), checks = emptyMap(),
                    selectionActors = emptyMap(), attemptNumber = quiz.attemptNumber + 1, pastAttempts = quiz.pastAttempts + saved, timer = PracticeTimer())
            }
            is LearningInteraction.ReviewQuizAttempt -> {
                val selected = if (event.attemptNumber == null) {
                    require(event.questionId == null); null
                } else {
                    val past = quiz.pastAttempts.firstOrNull { it.number == event.attemptNumber } ?: error("Unknown saved attempt")
                    val questionId = event.questionId ?: past.questions[past.index].id
                    require(past.questions.any { it.id == questionId }) { "Unknown question in saved attempt" }
                    QuizHistorySelection(past.number, questionId)
                }
                quiz.copy(review = selected)
            }
            else -> error("Not a quiz history action")
        }
        // Only persisted question snapshots are reused here; no source text is created or retargeted.
        QuizDataValidator.validate(updated, catalog, requireAvailableSources = false)
        val source = updated.review?.let { selected -> updated.pastAttempts.single { it.number == selected.attemptNumber }
            .questions.single { it.id == selected.questionId }.sources.first() } ?: updated.questions[updated.index].sources.first()
        return state.copy(revision = state.revision + 1, activities = state.activities + (id to activity.copy(data = updated, source = source)),
            context = state.context.copy(source = source, focusedActivityId = id,
                originalPdfVisible = state.context.originalPdfVisible && catalog?.contains(source.copy(elementId = null)) == true),
            focusedComponentId = state.components.values.first { it.activityId == id && it.visible }.id)
    }
}
