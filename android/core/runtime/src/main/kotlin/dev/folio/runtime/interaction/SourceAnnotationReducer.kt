package dev.folio.runtime.interaction

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.CommandActor
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.reading.SentenceIndex

class SourceAnnotationReducer(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val activityId = when (event) {
            is LearningInteraction.CreateAnnotation -> event.activityId
            is LearningInteraction.UpdateAnnotation -> event.activityId
            is LearningInteraction.RemoveAnnotation -> event.activityId
            else -> error("Not an annotation interaction")
        }
        val activity = state.activities[activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Annotations require a reader")
        val annotations = when (event) {
            is LearningInteraction.CreateAnnotation -> {
                require(event.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && event.id !in reader.annotations)
                require(reader.annotations.size < 200) { "This reader has reached its annotation limit" }
                require(event.source.documentId == activity.source.documentId && event.source.pageIndex == activity.source.pageIndex)
                val page = catalog.page(event.source.documentId, event.source.pageIndex) ?: error("Annotation page unavailable")
                require(page.elements.any { it.id == event.source.elementId && it.kind in setOf(
                    SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST) }) { "Unknown passage" }
                event.sentenceId?.let { id -> require(SentenceIndex().compile(page).any { it.id == id && it.source == event.source }) }
                validateText(event.kind, event.text)
                reader.annotations + (event.id to SourceAnnotation(event.id, event.source, event.sentenceId, event.kind,
                    event.text, if (actor == CommandActor.AI) AnnotationAuthor.AI else AnnotationAuthor.USER))
            }
            is LearningInteraction.UpdateAnnotation -> {
                val old = reader.annotations[event.id] ?: error("Unknown annotation")
                require(old.text == event.expectedText) { "Annotation changed; refresh before editing" }
                validateText(old.kind, event.text)
                reader.annotations + (old.id to old.copy(text = event.text))
            }
            is LearningInteraction.RemoveAnnotation -> {
                val old = reader.annotations[event.id] ?: error("Unknown annotation")
                require(old.text == event.expectedText) { "Annotation changed; refresh before removing" }
                reader.annotations - old.id
            }
            else -> error("Not an annotation interaction")
        }
        val drafts = if (actor == CommandActor.AI) reader.annotationDrafts else reader.annotationDrafts.filterValues { draft -> when (event) {
            is LearningInteraction.CreateAnnotation -> !(event.kind == AnnotationKind.NOTE && draft.annotationId == null &&
                draft.source == event.source && draft.sentenceId == event.sentenceId && draft.text == event.text)
            is LearningInteraction.UpdateAnnotation -> !(draft.annotationId == event.id && draft.text == event.text)
            is LearningInteraction.RemoveAnnotation -> true
            else -> true
        } }
        return state.copy(revision = state.revision + 1, activities = state.activities +
            (activityId to activity.copy(data = reader.copy(annotations = annotations, annotationDrafts = drafts))))
    }
    private fun validateText(kind: AnnotationKind, text: String) {
        require(text.length <= 2000 && (kind != AnnotationKind.NOTE || text.isNotBlank()))
    }
}
