package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.ReadingConfirmations
import dev.folio.ui.media.LocalSourceRevision
import java.text.DateFormat
import java.util.Date

/** Saved reports remain inspectable/clearable independently of the currently reconstructed page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingCheckpointShelf(activityId: String, reader: ActivityData.Reader, catalog: SourceCatalog,
    onInteraction: (LearningInteraction) -> Unit) {
    val sourceRevision = LocalSourceRevision.current
    TextButton({ onInteraction(LearningInteraction.ShowReadingCheckpoints(activityId, !reader.readingCheckpointsVisible)) },
        Modifier.testTag("reading-checkpoints-show-$activityId")) {
        Text("${if (reader.readingCheckpointsVisible) "Hide" else "Show"} reading checkpoints (${reader.readingConfirmations.size})")
    }
    if (!reader.readingCheckpointsVisible) return
    if (reader.readingConfirmations.isEmpty()) {
        Text("No confirmed reading checkpoints saved yet.", style = MaterialTheme.typography.bodySmall); return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("reading-checkpoint-list-$activityId"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(reader.readingConfirmations.values.sortedByDescending { it.version }, key = { it.id }) { record ->
            val available = remember(record, catalog, sourceRevision) { ReadingConfirmations.isCurrent(record, catalog) }
            val title = catalog.documents().firstOrNull { it.id == record.source.documentId }?.title ?: "Unavailable document"
            val time = remember(record.reportedAtEpochMillis) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.reportedAtEpochMillis))
            }
            Column(Modifier.testTag("reading-checkpoint-${record.id}")) {
                Text("$title · page ${record.source.pageIndex + 1}", style = MaterialTheme.typography.labelMedium)
                Text(if (record.sentenceId == null) "Passage reading report" else "Sentence reading report", style = MaterialTheme.typography.labelSmall)
                Text(record.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("$time · ${if (record.byAssistant) "Reported via assistant" else "Reported by you"}", style = MaterialTheme.typography.labelSmall)
                if (!available) Text("Saved wording changed or its source is unavailable. Your report is retained.",
                    Modifier.testTag("reading-checkpoint-unavailable-${record.id}"), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onInteraction(LearningInteraction.OpenReadingConfirmation(activityId, record.id, record.version)) }, enabled = available,
                        modifier = Modifier.testTag("reading-checkpoint-open-${record.id}")) { Text("Open checkpoint") }
                    TextButton({ onInteraction(LearningInteraction.ClearReadingConfirmation(activityId, record.id, record.version)) },
                        Modifier.testTag("reading-checkpoint-clear-${record.id}")) { Text("Clear reading report") }
                }
            }
        }
    }
}
