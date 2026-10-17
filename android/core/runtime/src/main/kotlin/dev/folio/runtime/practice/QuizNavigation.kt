package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class QuizNavigation(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, event: LearningInteraction.QuizQuestion): Workspace {
        val activity = state.activities[event.activityId] ?: error("Unknown quiz")
        val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
        val index = quiz.questions.indexOfFirst { it.id == event.questionId }
        require(index >= 0) { "Unknown question in this quiz" }
        require(activity.status == ActivityStatus.ACTIVE) { "Resume this quiz before choosing a question" }
        val component = state.components.values.firstOrNull { it.activityId == activity.id && it.visible }
            ?: error("Show this quiz before choosing a question")
        val source = quiz.questions[index].sources.first()
        // This selects an existing saved question; original-source navigation remains separately guarded.
        return state.copy(revision = state.revision + 1,
            activities = state.activities + (activity.id to activity.copy(source = source, data = quiz.copy(index = index, review = null))),
            context = state.context.copy(source = source, focusedActivityId = activity.id,
                originalPdfVisible = state.context.originalPdfVisible && catalog.contains(source.copy(elementId = null))), focusedComponentId = component.id)
    }
}
