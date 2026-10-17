package dev.folio.runtime.interaction

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.CommandActor
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace

class AnnotationDraftReducer(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, event: LearningInteraction.SaveAnnotationDraft, actor: CommandActor): Workspace {
        require(actor == CommandActor.USER) { "Unfinished drafts are controlled by the local editor" }
        val activity = state.activities[event.activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Draft requires a reader")
        require(event.draftId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        val draft = event.draft
        if (draft != null) {
            require(draft.text.length <= 2000 && (draft.expectedText?.length ?: 0) <= 2000)
            require(draft.source.documentId == activity.source.documentId && draft.source.pageIndex == activity.source.pageIndex)
            require(catalog.contains(draft.source) && draft.source.elementId != null)
            require(event.draftId in reader.annotationDrafts || reader.annotationDrafts.size < 200)
            draft.annotationId?.let { id ->
                val annotation = reader.annotations[id] ?: error("Unknown annotation")
                require(annotation.source == draft.source && draft.expectedText != null)
            }
        }
        val updated = if (draft == null) reader.annotationDrafts - event.draftId else reader.annotationDrafts + (event.draftId to draft)
        return state.copy(revision = state.revision + 1, activities = state.activities +
            (activity.id to activity.copy(data = reader.copy(annotationDrafts = updated))))
    }
}
