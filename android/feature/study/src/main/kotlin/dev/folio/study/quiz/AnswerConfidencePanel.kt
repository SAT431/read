package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.*
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.practice.confidenceLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerConfidencePanel(activityId: String, questionId: String, quiz: ActivityData.Quiz,
    onInteraction: (LearningInteraction) -> Unit) {
    val report = quiz.confidence[questionId]
    val answer = quiz.answers[questionId]
    val checked = questionId in quiz.submitted
    val editable = !checked && answer != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("How sure are you?", style = MaterialTheme.typography.titleSmall)
        Text(if (checked) "Your optional confidence report is locked with this checked answer."
            else if (answer == null) "Choose an answer first. Confidence is optional."
            else "Optional: report confidence before checking. Changing your answer clears this report.", style = MaterialTheme.typography.bodySmall)
        if (report == null) Text("Confidence not recorded", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConfidenceLevel.entries.forEach { level ->
                FilterChip(selected = report?.level == level, enabled = editable && report?.level != level,
                    onClick = { onInteraction(LearningInteraction.SetAnswerConfidence(activityId, questionId, requireNotNull(answer), report?.level, level)) },
                    label = { Text(confidenceLabel(level)) }, modifier = Modifier.testTag("quiz-confidence-$questionId-${level.name}"))
            }
            TextButton({ onInteraction(LearningInteraction.SetAnswerConfidence(activityId, questionId, requireNotNull(answer), report?.level, null)) },
                enabled = editable && report != null, modifier = Modifier.testTag("quiz-confidence-clear-$questionId")) { Text("Clear confidence") }
        }
        if (report?.byAssistant == true) Text("Reported confidence entered by assistant", style = MaterialTheme.typography.labelSmall)
    }
}
