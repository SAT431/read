package dev.folio.library.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.ui.control.SemanticButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedAnnotationCard(entry: SavedNoteEntry, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${entry.author?.name?.lowercase().orEmpty()} ${entry.kind.name.lowercase()}", style = MaterialTheme.typography.labelMedium)
            SavedNoteSource(entry, catalog)
            if (entry.text.isNotEmpty()) Text(entry.text)
            if (!entry.sourceAvailable) Text("Saved passage or sentence is unavailable. Your record retains its original source.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SemanticButton("notes-open-${entry.activityId}-${entry.id}", "Open saved source",
                    AppCommand.OpenSavedAnnotation(entry.activityId, entry.id, entry.fingerprint), dispatch, enabled = entry.sourceAvailable)
                if (entry.kind == SavedNoteKind.NOTE) SemanticButton("notes-edit-${entry.activityId}-${entry.id}", "Edit note",
                    AppCommand.BeginSavedNoteEdit(entry.activityId, entry.id, entry.fingerprint), dispatch)
            }
        }
    }
}
@Composable
internal fun SavedNoteSource(entry: SavedNoteEntry, catalog: SourceCatalog) {
    val title = catalog.documents().firstOrNull { it.id == entry.source.documentId }?.title ?: entry.source.documentId
    Text("$title · file page ${entry.source.pageIndex + 1} · ${if (entry.sentenceId == null) "passage" else "sentence"}", style = MaterialTheme.typography.bodySmall)
}
