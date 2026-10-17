package dev.folio.library.revision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.RevisionItem
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.practice.RevisionTargets
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RevisionQueueCard(item: RevisionItem, state: Workspace, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit) {
    val target = RevisionTargets.resolve(state, item.activityId, item.questionId)
    val available = target?.source == item.source && target?.prompt == item.prompt && catalog.contains(item.source)
    val document = catalog.documents().firstOrNull { it.id == item.source.documentId }
    var confirmRemove by remember(item.id, item.version) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().testTag("revision-item-${item.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (item.questionId == null) "Active recall" else "Quiz question", style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(item.prompt, style = MaterialTheme.typography.titleMedium) }
            Text("${document?.title ?: "Unavailable document"} · file page ${item.source.pageIndex + 1}")
            Text("Scheduled: ${date(item.dueAtEpochMillis)}")
            if (item.scheduledByAssistant) Text("Schedule entered by assistant", style = MaterialTheme.typography.labelSmall)
            if (!available) Text("The activity or source has changed or is unavailable. You can keep or remove this record; schedule the current activity again to update it.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ dispatch(AppCommand.OpenRevision(item.id)) }, Modifier.testTag("revision-open-${item.id}"), enabled = available) { Text("Open review") }
                if (!item.done) OutlinedButton({ dispatch(AppCommand.CompleteRevision(item.id, item.version)) },
                    Modifier.testTag("revision-complete-${item.id}"), enabled = item.completions.size < 100) { Text("Mark reviewed") }
            }
            item.suggestion?.let { accepted ->
                Text("Scheduled using a ${accepted.delayDays}-day practice suggestion.", style = MaterialTheme.typography.labelLarge)
                accepted.quizAttemptNumber?.let { Text("Recorded from quiz attempt $it", style = MaterialTheme.typography.labelSmall) }
                Text(dev.folio.ui.revision.revisionSuggestionReason(accepted), style = MaterialTheme.typography.bodySmall)
            }
            if (available) state.activities[item.activityId]?.let { activity ->
                dev.folio.runtime.practice.RevisionSuggestions.forActivity(activity, item.questionId)?.let { suggestion ->
                    dev.folio.ui.revision.RevisionSuggestionControl(suggestion) {
                        dispatch(AppCommand.AcceptRevisionSuggestion(item.activityId, item.questionId, suggestion.evidenceFingerprint))
                    }
                }
            }
            Text("Schedule another review", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0 to "Now", 1 to "In 1 day", 3 to "In 3 days", 7 to "In 7 days").forEach { (days, label) ->
                    OutlinedButton({ dispatch(AppCommand.ScheduleRevision(item.activityId, item.questionId, days)) },
                        Modifier.testTag("revision-reschedule-${item.id}-$days"), enabled = available) { Text(label) }
                }
            }
            if (item.completions.isNotEmpty()) {
                Text("Reported reviews: ${item.completions.size} · latest ${date(item.completions.last().completedAtEpochMillis)}")
                if (item.completions.last().byAssistant) Text("Latest completion entered by assistant", style = MaterialTheme.typography.labelSmall)
                var showHistory by remember(item.id) { mutableStateOf(false) }
                TextButton({ showHistory = !showHistory }) { Text(if (showHistory) "Hide review history" else "Show review history") }
                if (showHistory) item.completions.asReversed().forEach { completion ->
                    Text("${date(completion.completedAtEpochMillis)} · ${if (completion.byAssistant) "assistant-entered report" else "learner reported"}")
                    if (completion.prompt != null && (completion.prompt != item.prompt || completion.source != item.source)) {
                        Text("Earlier target: ${completion.prompt}")
                        completion.source?.let { source -> Text("${catalog.documents().firstOrNull { it.id == source.documentId }?.title ?: "Unavailable document"} · file page ${source.pageIndex + 1}") }
                    }
                }
            }
            TextButton({ confirmRemove = true }, Modifier.testTag("revision-remove-${item.id}")) { Text("Remove from revision") }
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Remove this revision item?") },
        text = { Text("This removes its schedule and reported review history. Your quiz answers and recall attempts stay saved.") },
        confirmButton = { TextButton({ confirmRemove = false; dispatch(AppCommand.RemoveRevision(item.id, item.version)) },
            Modifier.testTag("revision-confirm-remove")) { Text("Remove") } },
        dismissButton = { TextButton({ confirmRemove = false }) { Text("Keep") } })
}

private fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
