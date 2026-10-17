package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.runtime.interaction.LearningInteraction

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuizAttemptControls(activityId: String, quiz: ActivityData.Quiz, onInteraction: (LearningInteraction) -> Unit) {
    var confirm by remember(activityId, quiz.attemptNumber) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ confirm = true }, enabled = quiz.answers.isNotEmpty() && quiz.pastAttempts.size < 100,
            modifier = Modifier.testTag("quiz-restart-$activityId")) { Text("Start another attempt") }
        if (quiz.pastAttempts.size >= 100) Text("Saved-attempt limit reached. Current work and earlier attempts remain available.")
        if (quiz.pastAttempts.isNotEmpty()) {
            Text("Saved attempts (${quiz.pastAttempts.size})", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quiz.pastAttempts.forEach { saved ->
                    OutlinedButton({ onInteraction(LearningInteraction.ReviewQuizAttempt(activityId, saved.number, null)) },
                        Modifier.testTag("quiz-history-$activityId-${saved.number}")) {
                        Text("Attempt ${saved.number} · ${saved.submitted.size}/${saved.questions.size} checked")
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Start a fresh attempt?") },
        text = { Text("This attempt will be saved read-only, including unchecked choices and confidence. The new attempt starts with no answers or confidence. Earlier work stays available.") },
        confirmButton = { TextButton({ confirm = false; onInteraction(LearningInteraction.RestartQuiz(activityId, quiz.attemptNumber)) },
            Modifier.testTag("quiz-confirm-restart")) { Text("Save and start") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep working") } })
}
