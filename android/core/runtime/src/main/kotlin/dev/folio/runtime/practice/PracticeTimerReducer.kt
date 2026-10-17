package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class PracticeTimerReducer {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val (id, attempt, version) = when (event) {
            is LearningInteraction.SetPracticeTimerExpanded -> Triple(event.activityId, event.expectedAttempt, event.expectedVersion)
            is LearningInteraction.StartPracticeTimer -> Triple(event.activityId, event.expectedAttempt, event.expectedVersion)
            is LearningInteraction.PausePracticeTimer -> Triple(event.activityId, event.expectedAttempt, event.expectedVersion)
            is LearningInteraction.ResetPracticeTimer -> Triple(event.activityId, event.expectedAttempt, event.expectedVersion)
            is LearningInteraction.SetPracticeTimerLimit -> Triple(event.activityId, event.expectedAttempt, event.expectedVersion)
            else -> error("Not a timer control")
        }
        val activity = state.activities[id] ?: error("Unknown quiz")
        val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
        val timer = quiz.timer
        require(quiz.review == null && quiz.attemptNumber == attempt && timer.version == version) { "Timer or attempt changed; refresh before controlling it" }
        require(timer.version < PracticeTimerRules.MAX_VERSION - 1) { "Timer control limit reached; start another attempt" }
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any {
            it.activityId == id && it.visible && it.presentation != Presentation.CAPSULE
        }) { "Show and resume the current quiz first" }
        val base = timer.copy(version = timer.version + 1, changedByAssistant = actor == CommandActor.AI)
        val next = when (event) {
            is LearningInteraction.SetPracticeTimerExpanded -> timer.copy(detailsExpanded = event.expanded)
            is LearningInteraction.StartPracticeTimer -> {
                require(timer.status == TimerStatus.IDLE || timer.status == TimerStatus.PAUSED) { "Reset or extend an expired timer before starting" }
                base.copy(status = TimerStatus.RUNNING, lastChange = TimerChange.STARTED)
            }
            is LearningInteraction.PausePracticeTimer -> if (timer.status == TimerStatus.RUNNING)
                base.copy(status = TimerStatus.PAUSED, lastChange = TimerChange.PAUSED) else timer
            is LearningInteraction.ResetPracticeTimer -> base.copy(elapsedMillis = 0, status = TimerStatus.IDLE, lastChange = TimerChange.RESET)
            is LearningInteraction.SetPracticeTimerLimit -> {
                require(event.seconds?.let { it in 1..PracticeTimerRules.MAX_LIMIT_SECONDS } != false)
                val limit = event.seconds?.times(1000L)
                val status = when {
                    limit != null && timer.elapsedMillis >= limit -> TimerStatus.EXPIRED
                    timer.status == TimerStatus.EXPIRED -> TimerStatus.PAUSED
                    else -> timer.status
                }
                base.copy(limitMillis = limit, status = status, lastChange = TimerChange.LIMIT_SET)
            }
            else -> error("Not a timer control")
        }
        PracticeTimerRules.validate(next)
        return state.copy(revision = state.revision + 1, activities = state.activities + (id to activity.copy(data = quiz.copy(timer = next))),
            context = state.context.copy(source = activity.source, focusedActivityId = id),
            focusedComponentId = state.components.values.first { it.activityId == id && it.visible && it.presentation != Presentation.CAPSULE }.id)
    }
}
