package dev.folio.library.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.ui.control.SemanticButton
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedDraftCard(entry: SavedNoteEntry, draft: AnnotationDraft, notes: Map<String, SourceAnnotation>,
    catalog: SourceCatalog, dispatch: (AppCommand) -> Unit) {
    var editing by rememberSaveable(entry.activityId, entry.id) { mutableStateOf(false) }
    var discardFingerprint by rememberSaveable(entry.activityId, entry.id) { mutableStateOf<String?>(null) }
    val old = draft.annotationId?.let(notes::get)
    val conflict = draft.annotationId != null && (old == null || old.kind != AnnotationKind.NOTE || old.source != draft.source ||
        old.sentenceId != draft.sentenceId || old.text != draft.expectedText)
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Private ${if (draft.annotationId == null) "new-note draft" else "note edit"}", style = MaterialTheme.typography.labelMedium)
            SavedNoteSource(entry, catalog)
            Text(entry.text.ifBlank { "Empty saved draft" })
            if (!entry.sourceAvailable) Text("Original passage or sentence unavailable. Editing keeps the same saved source link.")
            if (conflict) Text("The original note changed or is missing. Your draft is retained. You can save it as a separate note.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ editing = true }) { Text("Edit private draft") }
                SemanticButton("notes-commit-${entry.activityId}-${entry.id}", if (draft.annotationId == null) "Save as note" else "Apply to original note",
                    AppCommand.CommitSavedDraft(entry.activityId, entry.id, entry.fingerprint, if (draft.annotationId == null) UUID.randomUUID().toString() else null),
                    dispatch, enabled = !conflict && draft.text.isNotBlank())
                if (conflict) SemanticButton("notes-copy-${entry.activityId}-${entry.id}", "Save as a separate note",
                    AppCommand.CommitSavedDraft(entry.activityId, entry.id, entry.fingerprint, UUID.randomUUID().toString(), asSeparateNote = true),
                    dispatch, enabled = draft.text.isNotBlank())
                TextButton({ discardFingerprint = entry.fingerprint }) { Text("Discard draft…") }
            }
        }
    }
    if (editing) SavedDraftEditor(entry, { editing = false }, dispatch)
    discardFingerprint?.let { expected -> AlertDialog(onDismissRequest = { discardFingerprint = null },
        title = { Text("Discard this private draft?") }, text = { Text("Only this saved draft will be removed. Existing notes and original source stay saved.") },
        confirmButton = { SemanticButton("notes-confirm-discard", "Discard draft", AppCommand.DiscardSavedDraft(entry.activityId, entry.id, expected), {
            dispatch(it); discardFingerprint = null
        }) }, dismissButton = { TextButton({ discardFingerprint = null }) { Text("Keep draft") } }) }
}
