package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction

class RecallReducer(private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val id = when (event) {
            is LearningInteraction.RecallResponse -> event.activityId
            is LearningInteraction.RevealRecall -> event.activityId
            is LearningInteraction.RateRecall -> event.activityId
            is LearningInteraction.RestartRecall -> event.activityId
            else -> error("Not a recall action")
        }
        val activity = state.activities[id] ?: error("Unknown recall activity")
        val recall = activity.data as? ActivityData.Recall ?: error("Not a recall card")
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any { it.activityId == id && it.visible }) {
            "Resume and show this recall card first"
        }
        val updated = when (event) {
            is LearningInteraction.RecallResponse -> {
                require(!recall.revealed && event.expectedText == recall.response) { "Recall response changed or is already revealed" }
                require(event.text.length <= 4000)
                recall.copy(response = event.text, responseByAssistant = event.text.isNotEmpty() && (recall.responseByAssistant || actor == CommandActor.AI))
            }
            is LearningInteraction.RevealRecall -> {
                require(!recall.revealed) { "Reference answer already revealed" }
                recall.copy(revealed = true)
            }
            is LearningInteraction.RateRecall -> {
                require(recall.revealed && recall.rating == null) { "Reveal the reference before reporting recall; reported attempts are locked" }
                val time = now(); require(time >= 0)
                recall.copy(rating = event.rating, ratedAtEpochMillis = time, ratingByAssistant = actor == CommandActor.AI)
            }
            is LearningInteraction.RestartRecall -> {
                require(recall.revealed && recall.rating != null && recall.ratedAtEpochMillis != null && recall.pastAttempts.size < 100) {
                    "Report the current recall before starting another attempt; history limit is 100"
                }
                recall.copy(response = "", revealed = false, rating = null, ratedAtEpochMillis = null,
                    responseByAssistant = false, ratingByAssistant = false, pastAttempts = recall.pastAttempts + RecallAttempt(
                        recall.response, requireNotNull(recall.rating), requireNotNull(recall.ratedAtEpochMillis), recall.responseByAssistant, recall.ratingByAssistant))
            }
            else -> error("Not a recall action")
        }
        return state.copy(revision = state.revision + 1, activities = state.activities + (id to activity.copy(data = updated)))
    }
}
