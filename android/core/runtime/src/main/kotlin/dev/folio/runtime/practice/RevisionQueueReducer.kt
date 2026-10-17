package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace

class RevisionQueueReducer(private val catalog: SourceCatalog, private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, command: AppCommand, actor: CommandActor): Workspace {
        if (command is AppCommand.AcceptRevisionSuggestion) {
            val activity = state.activities[command.activityId] ?: error("Unknown review activity")
            val suggestion = RevisionSuggestions.forActivity(activity, command.questionId) ?: error("No rated or checked evidence for a suggestion")
            require(suggestion.evidenceFingerprint == command.expectedEvidence) { "Review evidence changed; refresh the suggestion" }
            val scheduled = apply(state, AppCommand.ScheduleRevision(command.activityId, command.questionId, suggestion.delayDays), actor)
            val id = RevisionTargets.id(command.activityId, command.questionId)
            val item = scheduled.revisionItems.getValue(id).copy(suggestion = suggestion)
            val updated = scheduled.revisionItems + (id to item)
            RevisionQueueValidator.validate(updated)
            return scheduled.copy(revisionItems = updated)
        }
        val time = now(); require(time >= 0)
        val items = state.revisionItems
        val updated = when (command) {
            is AppCommand.ScheduleRevision -> {
                require(command.delayDays in 0..365)
                require(command.activityId.isNotBlank() && command.activityId.length <= 100)
                val target = RevisionTargets.resolve(state, command.activityId, command.questionId) ?: error("Unknown quiz question or recall card")
                require(catalog.contains(target.source)) { "Review source unavailable" }
                require(target.prompt.isNotBlank() && target.prompt.length <= 20000)
                require(command.questionId?.let { it.isNotBlank() && it.length <= 200 } != false)
                val delay = command.delayDays * 86_400_000L
                require(time <= Long.MAX_VALUE - delay)
                val id = RevisionTargets.id(command.activityId, command.questionId)
                val old = items[id]
                require(old != null || items.size < 1000) { "Revision queue limit reached" }
                require((old?.version ?: 0) < Int.MAX_VALUE)
                val item = RevisionItem(id, command.activityId, command.questionId, target.source, target.prompt,
                    time + delay, old?.createdAtEpochMillis ?: time, time, (old?.version ?: 0) + 1,
                    scheduledByAssistant = actor == CommandActor.AI, completions = old?.completions.orEmpty())
                items + (id to item)
            }
            is AppCommand.CompleteRevision -> {
                val item = items[command.id] ?: error("Unknown revision item")
                require(item.version == command.expectedVersion && item.version < Int.MAX_VALUE) { "Revision item changed; refresh before completing" }
                require(!item.done && item.completions.size < 100) { "Already reviewed or completion history limit reached" }
                items + (item.id to item.copy(done = true, version = item.version + 1, updatedAtEpochMillis = time,
                    completions = item.completions + RevisionCompletion(time, actor == CommandActor.AI, item.source, item.prompt)))
            }
            is AppCommand.RemoveRevision -> {
                val item = items[command.id] ?: error("Unknown revision item")
                require(item.version == command.expectedVersion) { "Revision item changed; refresh before removing" }
                items - item.id
            }
            else -> error("Not a revision mutation")
        }
        RevisionQueueValidator.validate(updated)
        return state.copy(revisionItems = updated)
    }
}
