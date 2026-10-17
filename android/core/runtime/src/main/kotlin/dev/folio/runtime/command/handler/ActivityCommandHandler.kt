package dev.folio.runtime.command.handler

import dev.folio.model.activity.*
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.ComponentInstance
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.*
import dev.folio.runtime.validation.*

class ActivityCommandHandler : CommandHandler {
    override fun supports(command: WorkspaceCommand) = command is WorkspaceCommand.CreateActivity ||
        command is WorkspaceCommand.ShowComponent || command is WorkspaceCommand.FocusActivity ||
        command is WorkspaceCommand.SuspendActivity || command is WorkspaceCommand.ResumeActivity

    override fun apply(state: Workspace, command: WorkspaceCommand, context: HandlerContext): Workspace = when (command) {
        is WorkspaceCommand.CreateActivity -> {
            CommandGuards.id(command.activityId); CommandGuards.id(command.componentId)
            require(command.activityId !in state.activities && command.componentId !in state.components) { "Duplicate instance" }
            context.registry.require(command.kind)
            require(ActivityValidator.kind(command.data) == command.kind) { "Component/data mismatch" }
            if (context.policy.actor == dev.folio.model.command.CommandActor.AI)
                (command.data as? ActivityData.Reader)?.let { require(it.annotations.isEmpty() && it.annotationDrafts.isEmpty() && it.bookmarks.isEmpty() &&
                    !it.bookmarksVisible && it.bookmarkJump == 0 && it.bookmarkRevision == 0L && it.readingConfirmations.isEmpty() &&
                    it.readingRevision == 0L && !it.readingCheckpointsVisible) { "Create annotations, bookmarks and reading reports through validated native commands" } }
            if (context.policy.actor == dev.folio.model.command.CommandActor.AI)
                (command.data as? ActivityData.Quiz)?.let { require(it.answers.isEmpty() && it.submitted.isEmpty() && it.confidence.isEmpty() && it.checks.isEmpty() &&
                    it.selectionActors.isEmpty() && it.pastAttempts.isEmpty() && it.attemptNumber == 1 && it.review == null && it.timer == PracticeTimer()) { "New AI quizzes cannot contain learner answers or submissions" } }
            if (context.policy.actor == dev.folio.model.command.CommandActor.AI)
                (command.data as? ActivityData.Recall)?.let { require(it.response.isEmpty() && !it.revealed && it.rating == null &&
                    it.ratedAtEpochMillis == null && !it.responseByAssistant && !it.ratingByAssistant && it.pastAttempts.isEmpty()) {
                    "New AI recall cards cannot contain learner responses, reveal state or history"
                } }
            if (context.policy.actor == dev.folio.model.command.CommandActor.AI && command.data !is ActivityData.Reader) {
                val references = listOf(command.source) + when (val data = command.data) {
                    is ActivityData.Quiz -> data.questions.flatMap { it.sources }
                    is ActivityData.Recall -> data.sources
                    else -> emptyList()
                }
                require(references.all { dev.folio.runtime.source.SavedSourceReferences.available(it, context.catalog) }) {
                    "Readable source content is unavailable. Restore or process the page before creating source-based content"
                }
            }
            ActivityValidator.validate(command.data, command.source, context.catalog)
            val source = (command.data as? ActivityData.Quiz)?.let { it.questions[it.index].sources.first() }
                ?: (command.data as? ActivityData.Recall)?.sources?.first() ?: command.source
            val activity = Activity(command.activityId, source, command.data)
            val component = ComponentInstance(command.componentId, command.activityId, command.kind)
            state.copy(activities = state.activities + (activity.id to activity),
                components = state.components + (component.id to component), focusedComponentId = component.id,
                context = state.context.copy(source = activity.source, focusedActivityId = activity.id))
        }
        is WorkspaceCommand.ShowComponent -> {
            CommandGuards.id(command.componentId)
            require(command.componentId !in state.components) { "Duplicate component instance" }
            val activity = state.activities[command.activityId] ?: error("Unknown activity")
            context.registry.require(command.kind)
            require(ActivityValidator.kind(activity.data) == command.kind)
            val component = ComponentInstance(command.componentId, activity.id, command.kind)
            state.copy(components = state.components + (component.id to component),
                activities = state.activities + (activity.id to activity.copy(status = ActivityStatus.ACTIVE)))
        }
        is WorkspaceCommand.SuspendActivity -> {
            val activity = state.activities[command.activityId] ?: error("Unknown activity")
            state.components.values.filter { it.activityId == activity.id }.forEach { CommandGuards.mayRearrange(it, context.policy) }
            state.copy(activities = state.activities + (activity.id to activity.copy(status = ActivityStatus.SUSPENDED)),
                components = state.components.mapValues { (_, c) -> if (c.activityId == activity.id) c.copy(visible = false) else c })
        }
        is WorkspaceCommand.FocusActivity -> activate(state, command.activityId)
        is WorkspaceCommand.ResumeActivity -> activate(state, command.activityId)
        else -> error("Unsupported activity command")
    }

    private fun activate(state: Workspace, id: String): Workspace {
        val activity = state.activities[id] ?: error("Unknown activity")
        val component = state.components.values.firstOrNull { it.activityId == id } ?: error("Activity has no component")
        return state.copy(activities = state.activities + (id to activity.copy(status = ActivityStatus.ACTIVE)),
            components = state.components + (component.id to component.copy(visible = true)),
            focusedComponentId = component.id, context = state.context.copy(source = activity.source, focusedActivityId = id))
    }
}
