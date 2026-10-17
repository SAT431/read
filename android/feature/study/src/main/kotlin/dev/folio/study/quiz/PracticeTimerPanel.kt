package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.*
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.practice.formatPracticeTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PracticeTimerPanel(activityId: String, quiz: ActivityData.Quiz, onInteraction: (LearningInteraction) -> Unit) {
    val timer = quiz.timer
    var editLimit by remember(activityId, quiz.attemptNumber) { mutableStateOf(false) }
    var confirmReset by remember(activityId, quiz.attemptNumber) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Practice timer · ${if (timer.limitMillis == null) "stopwatch" else "countdown"}", style = MaterialTheme.typography.titleSmall)
        Text("Elapsed ${formatPracticeTime(timer.elapsedMillis)}", Modifier.testTag("practice-timer-elapsed-$activityId"))
        timer.limitMillis?.let { Text("Remaining ${formatPracticeTime((it - timer.elapsedMillis).coerceAtLeast(0))}") }
        Text(when (timer.status) { TimerStatus.IDLE -> "Ready"; TimerStatus.RUNNING -> "Running"; TimerStatus.PAUSED -> "Paused"; TimerStatus.EXPIRED -> "Time limit reached" },
            Modifier.testTag("practice-timer-status-$activityId"), style = MaterialTheme.typography.labelLarge)
        if (timer.status == TimerStatus.EXPIRED) Text("Your answers remain available. Time limits do not check or submit them.", style = MaterialTheme.typography.bodySmall)
        if (timer.status == TimerStatus.PAUSED && timer.lastChange in setOf(TimerChange.BACKGROUNDED, TimerChange.SUSPENDED, TimerChange.HIDDEN,
                TimerChange.LEFT_PRACTICE, TimerChange.REVIEWING_HISTORY, TimerChange.RECOVERED, TimerChange.CLOCK_INTERRUPTED))
            Text(if (timer.lastChange == TimerChange.RECOVERED) "Restored at the last saved checkpoint. Resume when ready."
                else "Practice time paused automatically. Resume when ready.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ onInteraction(LearningInteraction.StartPracticeTimer(activityId, quiz.attemptNumber, timer.version)) },
                enabled = timer.status == TimerStatus.IDLE || timer.status == TimerStatus.PAUSED,
                modifier = Modifier.testTag("practice-timer-start-$activityId")) { Text(if (timer.status == TimerStatus.PAUSED) "Resume" else "Start") }
            OutlinedButton({ onInteraction(LearningInteraction.PausePracticeTimer(activityId, quiz.attemptNumber, timer.version)) },
                enabled = timer.status == TimerStatus.RUNNING, modifier = Modifier.testTag("practice-timer-pause-$activityId")) { Text("Pause") }
            TextButton({ onInteraction(LearningInteraction.SetPracticeTimerExpanded(activityId, quiz.attemptNumber, timer.version, !timer.detailsExpanded)) },
                Modifier.testTag("practice-timer-expand-$activityId")) { Text(if (timer.detailsExpanded) "Less" else "Timer options") }
        }
        if (timer.detailsExpanded) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton({ editLimit = true }, Modifier.testTag("practice-timer-limit-$activityId")) { Text("Set time") }
            TextButton({ confirmReset = true }, enabled = timer.elapsedMillis > 0 || timer.status != TimerStatus.IDLE,
                modifier = Modifier.testTag("practice-timer-reset-$activityId")) { Text("Reset time") }
        }
        if (timer.changedByAssistant) Text("Timer control applied by assistant", style = MaterialTheme.typography.labelSmall)
    }
    if (editLimit) TimerDurationDialog(activityId, quiz.attemptNumber, timer.limitMillis?.div(1000)?.toInt(), { editLimit = false }) { seconds ->
        editLimit = false; onInteraction(LearningInteraction.SetPracticeTimerLimit(activityId, quiz.attemptNumber, timer.version, seconds))
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset practice time?") },
        text = { Text("This clears only the current timer's elapsed time. Its time limit, answers, confidence and saved attempts stay unchanged.") },
        confirmButton = { TextButton({ confirmReset = false; onInteraction(LearningInteraction.ResetPracticeTimer(activityId, quiz.attemptNumber, timer.version)) },
            Modifier.testTag("practice-timer-confirm-reset")) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Keep time") } })
}
