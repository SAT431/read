package dev.folio.study.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.ReadingConfirmations

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuidedReadingMarker(activityId: String, source: SourceAnchor, sentenceId: String?, reader: ActivityData.Reader,
    progress: ReadingProgress, catalog: SourceCatalog, onInteraction: (LearningInteraction) -> Unit) {
    val id = ReadingConfirmations.id(source, sentenceId)
    val saved = reader.readingConfirmations[id]
    val current = saved?.takeIf { ReadingConfirmations.isCurrent(it, catalog) }
    val passageReport = if (sentenceId != null) reader.readingConfirmations[ReadingConfirmations.id(source, null)] else null
    val lastHere = reader.readingConfirmations[progress.lastConfirmedId]?.takeIf { it.source == source }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (lastHere != null) Row(Modifier.testTag("reading-marker-${source.elementId}")) {
            Box(Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(if (lastHere.sentenceId == null) "Last confirmed passage" else "Last confirmed sentence", style = MaterialTheme.typography.labelMedium)
                Text(lastHere.excerpt, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                if (lastHere.byAssistant) Text("Your report recorded by assistant", style = MaterialTheme.typography.labelSmall)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current == null) TextButton({ onInteraction(LearningInteraction.ConfirmReading(activityId, source, sentenceId, reader.readingRevision)) },
                Modifier.testTag("reading-confirm-${source.elementId}-${sentenceId ?: "passage"}")) {
                Text(if (sentenceId == null) "Confirm passage read" else "Confirm selected sentence read")
            }
            if (saved != null) TextButton({ onInteraction(LearningInteraction.ClearReadingConfirmation(activityId, saved.id, saved.version)) },
                Modifier.testTag("reading-clear-${saved.id}")) { Text(if (current == null) "Clear older confirmation" else "Clear reading confirmation") }
            if (passageReport != null) TextButton({ onInteraction(LearningInteraction.ClearReadingConfirmation(activityId, passageReport.id, passageReport.version)) },
                Modifier.testTag("reading-clear-${passageReport.id}")) { Text("Clear passage confirmation") }
        }
    }
}
