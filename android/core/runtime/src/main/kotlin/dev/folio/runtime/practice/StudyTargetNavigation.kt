package dev.folio.runtime.practice

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*

data class SavedStudyTarget(val activityId: String, val questionId: String?, val source: dev.folio.model.source.SourceAnchor, val prompt: String, val quizAttemptNumber: Int? = null)

class StudyTargetNavigation(private val executor: CommandExecutor, private val catalog: SourceCatalog, private val curriculum: CurriculumCatalog) {
    fun open(state: Workspace, item: SavedStudyTarget, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Workspace {
        val quiz = state.activities[item.activityId]?.data as? ActivityData.Quiz
        val pastNumber = item.quizAttemptNumber?.takeIf { it != quiz?.attemptNumber }
        val target = if (pastNumber != null) {
            val past = quiz?.pastAttempts?.firstOrNull { it.number == pastNumber } ?: error("Saved attempt unavailable")
            val question = past.questions.firstOrNull { it.id == item.questionId } ?: error("Saved question unavailable")
            RevisionTarget(question.sources.first(), question.prompt)
        } else RevisionTargets.resolve(state, item.activityId, item.questionId) ?: error("Review activity unavailable")
        require(target.source == item.source && target.prompt == item.prompt) { "Saved study target changed" }
        val storedView = state.components.values.firstOrNull { it.activityId == item.activityId } ?: error("Review view unavailable")
        if (!storedView.visible || storedView.presentation == Presentation.CAPSULE)
            dev.folio.runtime.validation.CommandGuards.mayRearrange(storedView, policy)
        val resumed = executor.execute(state, CommandBatch(requestId = "saved-study-open", commands = listOf(WorkspaceCommand.ResumeActivity(item.activityId))), policy)
        require(resumed.trace.accepted) { resumed.trace.error.orEmpty() }
        var next = resumed.state
        val component = next.components.getValue(requireNotNull(next.focusedComponentId))
        if (component.presentation == Presentation.CAPSULE) {
            val expanded = executor.execute(next, CommandBatch(requestId = "saved-study-expand", commands = listOf(
                WorkspaceCommand.SetPresentation(component.id, Presentation.SURFACE))), policy)
            require(expanded.trace.accepted) { expanded.trace.error.orEmpty() }; next = expanded.state
        }
        if (next.activities.getValue(item.activityId).data is ActivityData.Quiz)
            next = if (pastNumber != null) QuizHistoryReducer(catalog).apply(next,
                LearningInteraction.ReviewQuizAttempt(item.activityId, pastNumber, item.questionId), policy.actor)
            else LearningReducer(catalog).reduce(next, LearningInteraction.QuizQuestion(item.activityId, requireNotNull(item.questionId)), policy.actor)
        return StudyPdfContext.sync(next.copy(navigation = next.navigation.copy(destination = AppDestination.WORKSPACE)), target.source, pdfs, curriculum)
    }
}
