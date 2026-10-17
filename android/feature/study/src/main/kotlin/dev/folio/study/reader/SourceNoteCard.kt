package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.source.SourceAnnotation
import dev.folio.model.source.AnnotationDraft
import dev.folio.runtime.interaction.LearningInteraction

@Composable
internal fun SourceNoteCard(activityId: String, annotation: SourceAnnotation, stale: Boolean, recovered: AnnotationDraft?,
    onInteraction: (LearningInteraction) -> Unit) {
    var editing by rememberSaveable(annotation.id) { mutableStateOf(recovered != null) }
    var draft by rememberSaveable(annotation.id) { mutableStateOf(recovered?.text ?: annotation.text) }
    var expected by rememberSaveable(annotation.id) { mutableStateOf(recovered?.expectedText ?: annotation.text) }
    val protect = dev.folio.ui.interaction.rememberWritingProtection(editing)
    fun saveDraft() = onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, "edit-${annotation.id}",
        AnnotationDraft(annotation.source, annotation.sentenceId, draft, annotation.id, expected)))
    LaunchedEffect(annotation.text) {
        if (annotation.text == draft && editing) {
            editing = false; protect(false)
            if (recovered != null) onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, "edit-${annotation.id}", null))
        }
    }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${annotation.author.name.lowercase()} note · ${if (annotation.sentenceId == null) "passage" else "sentence"}",
                style = MaterialTheme.typography.labelSmall)
            if (stale) Text("The source sentence changed. Your note is retained.")
            if (editing && expected != annotation.text) Text("This note changed while you were editing. Your draft is retained; cancel to reload it.")
            if (editing) OutlinedTextField(draft, { draft = it.take(2000); protect(true); saveDraft() }, Modifier.fillMaxWidth(), label = { Text("Edit note") })
            else Text(annotation.text)
            Row {
                if (editing) {
                    TextButton({ onInteraction(LearningInteraction.UpdateAnnotation(activityId, annotation.id, expected, draft)) },
                        enabled = draft.isNotBlank() && draft != expected) { Text("Save changes") }
                    TextButton({ editing = false; protect(false)
                        onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, "edit-${annotation.id}", null)) }) { Text("Cancel edit") }
                } else TextButton({ expected = annotation.text; draft = annotation.text; editing = true; protect(true); saveDraft() }) { Text("Edit note") }
                TextButton({ onInteraction(LearningInteraction.RemoveAnnotation(activityId, annotation.id, annotation.text)) }) { Text("Delete note") }
            }
        }
    }
}
