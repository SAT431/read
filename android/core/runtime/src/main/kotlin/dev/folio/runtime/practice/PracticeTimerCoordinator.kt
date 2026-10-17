package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.*

/** Process-owned monotonic anchors; persisted checkpoints carry counted time only. */
class PracticeTimerCoordinator(private val clockMillis: () -> Long) {
    private data class Anchor(val attempt: Int, val version: Int, val at: Long)
    private val anchors = mutableMapOf<String, Anchor>()
    fun checkpoint(state: Workspace, foreground: Boolean): Workspace {
        val now = clockMillis()
        var semanticChange = false
        val running = mutableSetOf<String>()
        val activities = state.activities.mapValues { (id, activity) ->
            val quiz = activity.data as? ActivityData.Quiz ?: return@mapValues activity
            if (quiz.timer.status != TimerStatus.RUNNING) return@mapValues activity
            val anchor = anchors[id]?.takeIf { it.attempt == quiz.attemptNumber && it.version == quiz.timer.version }
            var timer = quiz.timer
            if (anchor != null) timer = if (now < anchor.at) PracticeTimerRules.pause(timer, TimerChange.CLOCK_INTERRUPTED)
                else PracticeTimerRules.advance(timer, (now - anchor.at).takeIf { it >= 0 } ?: PracticeTimerRules.MAX_ELAPSED)
            if (timer.status == TimerStatus.RUNNING) ineligibleReason(state, activity, quiz, foreground)?.let {
                timer = PracticeTimerRules.pause(timer, it)
            }
            if (timer.status == TimerStatus.RUNNING) {
                running.add(id); anchors[id] = Anchor(quiz.attemptNumber, timer.version, now)
            }
            if (timer == quiz.timer) activity else {
                semanticChange = semanticChange || timer.version != quiz.timer.version
                activity.copy(data = quiz.copy(timer = timer))
            }
        }
        anchors.keys.retainAll(running)
        return if (activities == state.activities) state else state.copy(activities = activities,
            revision = state.revision + if (semanticChange) 1 else 0)
    }
    fun recover(state: Workspace): Workspace {
        anchors.clear()
        val activities = state.activities.mapValues { (_, activity) ->
            val quiz = activity.data as? ActivityData.Quiz ?: return@mapValues activity
            val paused = PracticeTimerRules.pause(quiz.timer, TimerChange.RECOVERED)
            if (paused == quiz.timer) activity else activity.copy(data = quiz.copy(timer = paused))
        }
        return if (activities == state.activities) state else state.copy(activities = activities, revision = state.revision + 1)
    }
    private fun ineligibleReason(state: Workspace, activity: Activity, quiz: ActivityData.Quiz, foreground: Boolean): TimerChange? = when {
        !foreground -> TimerChange.BACKGROUNDED
        activity.status != ActivityStatus.ACTIVE -> TimerChange.SUSPENDED
        quiz.review != null -> TimerChange.REVIEWING_HISTORY
        state.components.values.none { it.activityId == activity.id && it.visible && it.presentation != Presentation.CAPSULE } -> TimerChange.HIDDEN
        state.navigation.destination != AppDestination.WORKSPACE && !(state.navigation.destination == AppDestination.ASSISTANT &&
            state.navigation.assistantOrigin == AppDestination.WORKSPACE) -> TimerChange.LEFT_PRACTICE
        else -> null
    }
}
