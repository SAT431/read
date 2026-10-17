package dev.folio.library.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.ui.practice.confidenceLabel
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LearningObservationCard(observation: LearningObservation, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit, historicalQuiz: Boolean = false) {
    val available = catalog.contains(observation.source)
    ElevatedCard(Modifier.fillMaxWidth().testTag("learning-observation-${observation.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (observation.kind) {
                ObservationKind.CHECKED_CORRECT -> "Checked as correct"
                ObservationKind.CHECKED_INCORRECT -> "Checked as incorrect"
                ObservationKind.RECALL_REPORT -> "Reported recall · attempt ${observation.attemptNumber}"
            }, style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(observation.prompt, style = MaterialTheme.typography.titleMedium) }
            val document = catalog.documents().firstOrNull { it.id == observation.source.documentId }
            Text("${document?.title ?: "Unavailable document"} · file page ${observation.source.pageIndex + 1}")
            if (observation.kind != ObservationKind.RECALL_REPORT) {
                Text("Quiz attempt ${observation.attemptNumber}${if (historicalQuiz) " · saved read-only" else ""}", style = MaterialTheme.typography.labelLarge)
                observation.reportedAtEpochMillis?.let { Text("Checked: ${date(it)}", style = MaterialTheme.typography.bodySmall) }
                if (observation.checkByAssistant == true) Text("Answer check requested by assistant", style = MaterialTheme.typography.labelSmall)
                if (observation.selectionByAssistant == true) Text("Answer selection entered by assistant", style = MaterialTheme.typography.labelSmall)
                Text("Confidence: ${observation.confidence?.level?.let(::confidenceLabel) ?: "not recorded"}")
                observation.confidence?.let { report ->
                    Text("Confidence reported: ${date(report.recordedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                    if (report.byAssistant) Text("Reported confidence entered by assistant", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("Rating reported: ${when (observation.recallRating) {
                    RecallRating.FORGOT -> "Forgot"; RecallRating.PARTIAL -> "Partly recalled"; RecallRating.RECALLED -> "Recalled"; null -> "not recorded"
                }}")
                observation.reportedAtEpochMillis?.let { Text("Reported: ${date(it)}", style = MaterialTheme.typography.bodySmall) }
                if (observation.reportedByAssistant) Text("Reported rating entered by assistant", style = MaterialTheme.typography.labelSmall)
                Text("Opening resumes the current recall card and keeps its earlier attempts.", style = MaterialTheme.typography.bodySmall)
            }
            if (!available) Text("The source is unavailable. This saved observation remains visible.")
            Button({ dispatch(AppCommand.OpenLearningObservation(observation.id)) }, enabled = available,
                modifier = Modifier.testTag("learning-open-${observation.id}")) {
                Text(if (observation.questionId == null) "Open current recall card" else if (historicalQuiz) "View saved answer" else "Open saved question")
            }
        }
    }
}

private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
