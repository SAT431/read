package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class QuizConfidenceReducer(private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, event: LearningInteraction.SetAnswerConfidence, actor: CommandActor): Workspace {
        val activity = state.activities[event.activityId] ?: error("Unknown quiz")
        val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any { it.activityId == activity.id && it.visible }) {
            "Show and resume the quiz before reporting confidence"
        }
        require(quiz.review == null) { "Saved attempts are read-only" }
        require(quiz.questions[quiz.index].id == event.questionId) { "Choose this question before reporting confidence" }
        require(event.expectedOption in 0..3 && quiz.answers[event.questionId] == event.expectedOption) { "Selected answer changed; refresh before reporting confidence" }
        require(event.questionId !in quiz.submitted) { "Confidence is locked after checking the answer" }
        require(quiz.confidence[event.questionId]?.level == event.expectedConfidence) { "Confidence changed; refresh before editing" }
        val updated = if (event.confidence == null) quiz.confidence - event.questionId else {
            val time = now(); require(time >= 0)
            quiz.confidence + (event.questionId to AnswerConfidence(event.confidence, event.expectedOption, time, actor == CommandActor.AI))
        }
        return state.copy(revision = state.revision + 1, activities = state.activities + (activity.id to activity.copy(data = quiz.copy(confidence = updated))))
    }
}
