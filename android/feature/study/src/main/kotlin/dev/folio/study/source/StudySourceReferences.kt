package dev.folio.study.source

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor

@Composable
internal fun StudySourceReferences(activityId: String, sources: List<SourceAnchor>, catalog: SourceCatalog,
    onCommands: (List<WorkspaceCommand>) -> Unit, tagPrefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val sourceRevision = dev.folio.ui.media.LocalSourceRevision.current
        Text("Supporting source", style = MaterialTheme.typography.labelMedium)
        sources.forEachIndexed { index, source ->
            val available = remember(source, catalog, sourceRevision) { runCatching { catalog.contains(source) }.getOrDefault(false) }
            val title = catalog.documents().firstOrNull { it.id == source.documentId }?.title ?: "Source"
            Text("$title · file page ${source.pageIndex + 1}${if (source.elementId == null) "" else " · linked passage"}")
            if (!available) Text("This saved source link is unavailable. Your study item is retained.", style = MaterialTheme.typography.bodySmall)
            FolioActionButton(if (index == 0) "Pause and review source" else "Review source ${index + 1}", {
                onCommands(listOf(WorkspaceCommand.SuspendActivity(activityId), WorkspaceCommand.FocusSource(source)))
            }, modifier = Modifier.testTag("$tagPrefix-$index"), enabled = available)
        }
    }
}
