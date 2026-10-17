package dev.folio.library.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.folio.model.command.AppCommand
import dev.folio.model.source.SavedNoteEntry
import dev.folio.ui.control.SemanticButton
import dev.folio.ui.interaction.rememberWritingProtection

/** The retained record changes only on explicit Keep draft changes; the edit buffer survives rotation. */
@Composable
internal fun SavedDraftEditor(entry: SavedNoteEntry, close: () -> Unit, dispatch: (AppCommand) -> Unit) {
    var text by rememberSaveable(entry.activityId, entry.id) { mutableStateOf(entry.text) }
    val expected = rememberSaveable(entry.activityId, entry.id) { entry.fingerprint }
    var submitted by rememberSaveable(entry.activityId, entry.id) { mutableStateOf(false) }
    rememberWritingProtection(true)
    LaunchedEffect(entry.fingerprint) { if (submitted && entry.text == text && expected != entry.fingerprint) close() }
    AlertDialog(onDismissRequest = close, title = { Text("Edit private draft") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Tap Keep draft changes to save. Closing keeps the previously saved draft.")
            if (entry.fingerprint != expected) Text("The saved draft changed. This text has not been saved. Copy it before closing and reopening.")
            OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth().testTag("notes-draft-editor"), label = { Text("Private draft text") }, minLines = 3, maxLines = 8)
        }
    }, confirmButton = {
        SemanticButton("notes-save-draft", "Keep draft changes", AppCommand.EditSavedDraft(entry.activityId, entry.id, expected, text), {
            submitted = true; dispatch(it)
        }, enabled = entry.fingerprint == expected && text != entry.text)
    }, dismissButton = { TextButton(close) { Text("Close editor") } })
}
