package dev.folio.ui.revision

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.*

fun revisionSuggestionReason(suggestion: RevisionSuggestion): String = when (suggestion.reason) {
    RevisionReason.QUIZ_INCORRECT -> "Your checked answer was incorrect."
    RevisionReason.QUIZ_CORRECT -> "Your checked answer was correct."
    RevisionReason.RECALL_FORGOT -> "You reported forgetting on this attempt."
    RevisionReason.RECALL_PARTIAL -> "You reported partial recall on this attempt."
    RevisionReason.RECALL_RECALLED -> "You reported recalling on ${suggestion.recalledStreak} consecutive attempt${if (suggestion.recalledStreak == 1) "" else "s"}."
}

@Composable
fun RevisionSuggestionControl(suggestion: RevisionSuggestion, onAccept: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Suggested review: in ${suggestion.delayDays} day${if (suggestion.delayDays == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
        suggestion.quizAttemptNumber?.let { Text("Based on quiz attempt $it", style = MaterialTheme.typography.labelSmall) }
        Text(revisionSuggestionReason(suggestion), style = MaterialTheme.typography.bodySmall)
        Text("An adjustable interval based on recorded practice. You can choose another time.", style = MaterialTheme.typography.bodySmall)
        if (suggestion.includesAssistantRatings) Text("Includes reported ratings entered by assistant.", style = MaterialTheme.typography.labelSmall)
        OutlinedButton(onAccept, Modifier.testTag("revision-suggestion-${suggestion.activityId}-${suggestion.questionId ?: "recall"}")) {
            Text("Use suggested interval")
        }
    }
}
