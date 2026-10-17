package dev.folio.library.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.reading.SavedNotes
import dev.folio.ui.media.LocalSourceRevision

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedNotesScreen(state: Workspace, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit) {
    val revision = LocalSourceRevision.current
    val entries = remember(state.activities, revision) { SavedNotes.entries(state, catalog) }
    val shown = remember(entries, state.navigation.notesFilter, state.navigation.notesQuery) {
        entries.filter { SavedNotes.matches(it, state.navigation.notesFilter, state.navigation.notesQuery) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("saved-notes"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved notes and drafts", style = MaterialTheme.typography.headlineMedium)
                Text("${entries.count { it.kind == SavedNoteKind.NOTE }} notes · ${entries.count { it.kind == SavedNoteKind.DRAFT }} private drafts · ${entries.count { it.kind in setOf(SavedNoteKind.HIGHLIGHT, SavedNoteKind.UNDERLINE) }} marks")
                Text("Unfinished drafts stay private from Gemini. Unavailable passages never hide your saved text.")
                Text("Passage links open the current wording, which may have changed since the note was saved.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(state.navigation.notesQuery, { dispatch(AppCommand.SearchNotes(it.take(200))) }, Modifier.fillMaxWidth().testTag("notes-search"),
                    label = { Text("Search saved text or source") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotesFilter.entries.forEach { filter -> FilterChip(filter == state.navigation.notesFilter,
                        { dispatch(AppCommand.SetNotesFilter(filter)) }, label = { Text(when (filter) {
                            NotesFilter.ALL -> "All"; NotesFilter.NOTES -> "Notes"; NotesFilter.MARKS -> "Marks"
                            NotesFilter.DRAFTS -> "Private drafts"; NotesFilter.UNAVAILABLE -> "Unavailable source"
                        }) }, modifier = Modifier.testTag("notes-filter-${filter.name}")) }
                }
                Text("${shown.size} saved ${if (shown.size == 1) "record" else "records"}", Modifier.testTag("notes-count"))
            }
        }
        if (shown.isEmpty()) item("empty") { Text(if (entries.isEmpty()) "Add notes or marks while reading. Saved drafts will appear here too." else "No saved records match this search and filter.") }
        items(shown, key = { "${it.activityId.length}:${it.activityId}:${it.kind}:${it.id}" }) { entry ->
            if (entry.kind == SavedNoteKind.DRAFT) {
                val reader = state.activities.getValue(entry.activityId).data as dev.folio.model.activity.ActivityData.Reader
                SavedDraftCard(entry, reader.annotationDrafts.getValue(entry.id), reader.annotations, catalog, dispatch)
            } else SavedAnnotationCard(entry, catalog, dispatch)
        }
    }
}
