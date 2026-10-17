package dev.folio.study.revision

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.RevisionItem
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.practice.RevisionTargets
import java.text.DateFormat
import java.util.Date

/** Explicit timing controls shared by prebuilt quiz and recall renderers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevisionSchedulePanel(activityId: String, questionId: String?, items: Map<String, RevisionItem>,
    onInteraction: (LearningInteraction) -> Unit, suggestion: dev.folio.model.activity.RevisionSuggestion? = null) {
    val item = items[RevisionTargets.id(activityId, questionId)]
    val tag = "revision-schedule-$activityId-${questionId ?: "recall"}"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Review later", style = MaterialTheme.typography.titleSmall)
        Text("Choose when to return. Your answers and attempts stay saved.", style = MaterialTheme.typography.bodySmall)
        if (item != null) Text(if (item.done) "Last review reported complete. Choose another time."
            else "Scheduled: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.dueAtEpochMillis))}",
            style = MaterialTheme.typography.bodySmall)
        suggestion?.let { proposed -> dev.folio.ui.revision.RevisionSuggestionControl(proposed) {
            onInteraction(LearningInteraction.AcceptRevisionSuggestion(activityId, questionId, proposed.evidenceFingerprint))
        } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0 to "Now", 1 to "In 1 day", 3 to "In 3 days", 7 to "In 7 days").forEach { (days, title) ->
                OutlinedButton({ onInteraction(LearningInteraction.ScheduleRevision(activityId, questionId, days)) },
                    Modifier.testTag("$tag-$days")) { Text(title) }
            }
        }
        Text("Days are 24-hour intervals. Find scheduled items in Revision.", style = MaterialTheme.typography.bodySmall)
    }
}
