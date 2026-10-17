package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.source.*
import dev.folio.runtime.interaction.LearningInteraction
import java.util.UUID

/** Notes belong to the reader activity; this view owns only unfinished editor text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceAnnotationPanel(activityId: String, source: SourceAnchor, sentences: List<SourceSentence>,
    selectedSentence: String?, annotations: List<SourceAnnotation>, drafts: Map<String, AnnotationDraft> = emptyMap(),
    onInteraction: (LearningInteraction) -> Unit) {
    val draftId = "new-${source.elementId}"
    val recovered = drafts[draftId]
    var draft by rememberSaveable(activityId, source) { mutableStateOf(recovered?.text.orEmpty()) }
    var pending by rememberSaveable(activityId, source) { mutableStateOf<String?>(null) }
    var draftSentence by rememberSaveable(activityId, source) { mutableStateOf(recovered?.sentenceId) }
    var expanded by rememberSaveable(activityId, source) { mutableStateOf(recovered != null) }
    val protect = dev.folio.ui.interaction.rememberWritingProtection(draft.isNotEmpty())
    LaunchedEffect(annotations) {
        if (pending != null && annotations.any { it.id == pending }) { draft = ""; pending = null }
    }
    val sentence = sentences.firstOrNull { it.id == selectedSentence }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide annotations" else "Annotations (${annotations.size})") }
        if (!expanded) return@Column
        Text(if (sentence == null) "Annotate passage" else "Annotate selected sentence", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AnnotationKind.HIGHLIGHT, AnnotationKind.UNDERLINE).forEach { kind ->
                val existing = annotations.firstOrNull { it.kind == kind && it.sentenceId == sentence?.id }
                TextButton(onClick = {
                    if (existing == null) onInteraction(LearningInteraction.CreateAnnotation(activityId, UUID.randomUUID().toString(), source,
                        sentence?.id, kind, ""))
                    else onInteraction(LearningInteraction.RemoveAnnotation(activityId, existing.id, existing.text))
                }) { Text((if (existing == null) "" else "Remove ") + kind.name.lowercase()) }
            }
        }
        OutlinedTextField(draft, {
            if (draft.isEmpty()) draftSentence = sentence?.id
            draft = it.take(2000)
            protect(draft.isNotEmpty())
            onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, draftId,
                if (draft.isEmpty()) null else AnnotationDraft(source, draftSentence, draft)))
        }, Modifier.fillMaxWidth().testTag("note-draft-${source.elementId}"),
            label = { Text("Add a note") }, minLines = 2, maxLines = 5)
        TextButton(onClick = {
            val id = pending ?: UUID.randomUUID().toString()
            pending = id
            onInteraction(LearningInteraction.CreateAnnotation(activityId, id, source, draftSentence, AnnotationKind.NOTE, draft))
        }, enabled = draft.isNotBlank()) { Text("Save note") }
        if (draft.isNotEmpty()) {
            Text("Draft saved locally. Finish or discard it before asking the assistant to move the workspace.")
            if (draftSentence != null && sentences.none { it.id == draftSentence }) {
                Text("The draft's source sentence changed. Your text is retained.")
                TextButton({
                    draftSentence = null
                    onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, draftId, AnnotationDraft(source, null, draft)))
                }) { Text("Attach draft to passage") }
            }
            TextButton({
                draft = ""; pending = null; protect(false)
                onInteraction(LearningInteraction.SaveAnnotationDraft(activityId, draftId, null))
            }) { Text("Discard draft") }
        }
        annotations.filter { it.kind == AnnotationKind.NOTE }.forEach { annotation ->
            key(annotation.id) { SourceNoteCard(activityId, annotation,
                annotation.sentenceId != null && sentences.none { it.id == annotation.sentenceId }, drafts["edit-${annotation.id}"], onInteraction) }
        }
        annotations.filter { it.kind != AnnotationKind.NOTE && it.sentenceId != null && sentences.none { s -> s.id == it.sentenceId } }.forEach { annotation ->
            Text("Saved ${annotation.kind.name.lowercase()} · source sentence changed")
            TextButton({ onInteraction(LearningInteraction.RemoveAnnotation(activityId, annotation.id, annotation.text)) }) { Text("Remove saved mark") }
        }
    }
}
