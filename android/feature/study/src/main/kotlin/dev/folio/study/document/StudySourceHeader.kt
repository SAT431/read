package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.ui.control.semanticControl

@Composable
internal fun StudySourceHeader(
    row: StudyReadingRow.Source,
    plan: DocumentStudyPlan,
    added: Boolean,
    dispatch: (AppCommand) -> Unit
) {
    val section = row.section
    val focus = AppCommand.StudyPage(plan.pdfId, plan.version, section.pageIndex, section.pdfId)
    val original = AppCommand.HarnessInspectPage(section.pdfId, section.pageIndex)

    // Keep source link ultra-compact without repeating the PDF filename at the top of the study canvas
    if (added) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("study-inline-source-${section.pdfId}-${section.pageIndex}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val selectedTopicTitle = section.topics.firstOrNull()?.title ?: "Page ${section.pageIndex + 1}"
        TextButton(
            onClick = { dispatch(focus) },
            modifier = Modifier
                .weight(1f)
                .semanticControl("study-source-focus-${section.pdfId}-${section.pageIndex}", focus),
            enabled = row.status != null,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (added) "Added · $selectedTopicTitle (p. ${section.pageIndex + 1})" else "$selectedTopicTitle · p. ${section.pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = { dispatch(original) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            modifier = Modifier.semanticControl("study-source-original-${section.pdfId}-${section.pageIndex}", original)
        ) {
            Text("Source p. ${section.pageIndex + 1}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
