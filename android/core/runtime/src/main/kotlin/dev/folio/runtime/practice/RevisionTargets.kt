package dev.folio.runtime.practice

import dev.folio.model.activity.ActivityData
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import java.security.MessageDigest

data class RevisionTarget(val source: SourceAnchor, val prompt: String)

object RevisionTargets {
    fun id(activityId: String, questionId: String?) = "revision-" + MessageDigest.getInstance("SHA-256")
        .digest("$activityId\u0000${questionId?.let { "q:$it" } ?: "recall"}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun resolve(state: Workspace, activityId: String, questionId: String?): RevisionTarget? = when (val data = state.activities[activityId]?.data) {
        is ActivityData.Quiz -> data.questions.firstOrNull { it.id == questionId }?.let { it.sources.firstOrNull()?.let { source -> RevisionTarget(source, it.prompt) } }
        is ActivityData.Recall -> if (questionId == null) data.sources.firstOrNull()?.let { RevisionTarget(it, data.prompt) } else null
        else -> null
    }
}
