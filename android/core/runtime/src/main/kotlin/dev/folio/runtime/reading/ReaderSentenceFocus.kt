package dev.folio.runtime.reading

import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class ReaderSentenceFocus(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, event: LearningInteraction.FocusSentence): Workspace {
        val activity = state.activities[event.activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Not a reader")
        val page = catalog.page(activity.source.documentId, activity.source.pageIndex) ?: error("Sentence source unavailable")
        val sentence = SentenceIndex().compile(page).firstOrNull { it.id == event.sentenceId } ?: error("Unknown sentence on reader page")
        val component = state.components.values.firstOrNull { it.activityId == activity.id && it.visible }
        return state.copy(revision = state.revision + 1,
            activities = state.activities + (activity.id to activity.copy(source = sentence.source,
                data = reader.copy(selectedSentence = sentence.id))),
            focusedComponentId = component?.id ?: state.focusedComponentId,
            context = state.context.copy(source = sentence.source, focusedActivityId = activity.id))
    }
}
