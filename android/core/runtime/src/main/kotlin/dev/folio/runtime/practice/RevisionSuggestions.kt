package dev.folio.runtime.practice

import dev.folio.model.activity.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** v1 adjustable intervals. Only submitted answers and explicitly rated recall attempts count. */
object RevisionSuggestions {
    fun forActivity(activity: Activity, questionId: String?): RevisionSuggestion? {
        var streak = 0
        var assistantRatings = false
        val observations: Int
        val days: Int
        val reason: RevisionReason
        val evidence: JsonObject
        when (val data = activity.data) {
            is ActivityData.Quiz -> {
                if (data.review != null) return null
                val question = data.questions.firstOrNull { it.id == questionId } ?: return null
                val answer = data.answers[question.id]?.takeIf { question.id in data.submitted } ?: return null
                if (answer !in question.options.indices) return null
                val correct = answer == question.correctOption
                reason = if (correct) RevisionReason.QUIZ_CORRECT else RevisionReason.QUIZ_INCORRECT
                days = if (correct) 3 else 1; observations = 1
                evidence = buildJsonObject { put("question", Json.encodeToJsonElement(question)); put("checkedAnswer", answer); put("attemptNumber", data.attemptNumber);
                    put("check", Json.encodeToJsonElement(data.checks[question.id])) }
            }
            is ActivityData.Recall -> {
                if (questionId != null || !data.revealed) return null
                val rating = data.rating ?: return null
                val time = data.ratedAtEpochMillis ?: return null
                val ratings = data.pastAttempts.map { it.rating } + rating
                observations = ratings.size
                streak = ratings.asReversed().takeWhile { it == RecallRating.RECALLED }.size
                assistantRatings = data.ratingByAssistant || data.pastAttempts.any { it.ratingByAssistant }
                reason = when (rating) {
                    RecallRating.FORGOT -> RevisionReason.RECALL_FORGOT
                    RecallRating.PARTIAL -> RevisionReason.RECALL_PARTIAL
                    RecallRating.RECALLED -> RevisionReason.RECALL_RECALLED
                }
                days = when (rating) {
                    RecallRating.FORGOT -> 1
                    RecallRating.PARTIAL -> 3
                    RecallRating.RECALLED -> listOf(7, 14, 30, 60, 90)[(streak - 1).coerceIn(0, 4)]
                }
                evidence = buildJsonObject {
                    put("prompt", data.prompt); put("reference", data.referenceAnswer)
                    put("sources", Json.encodeToJsonElement(data.sources))
                    put("ratings", buildJsonArray {
                        data.pastAttempts.forEach { add(buildJsonObject {
                            put("rating", it.rating.name); put("time", it.ratedAtEpochMillis); put("assistant", it.ratingByAssistant)
                        }) }
                        add(buildJsonObject { put("rating", rating.name); put("time", time); put("assistant", data.ratingByAssistant) })
                    })
                }
            }
            else -> return null
        }
        val identity = buildJsonObject {
            put("policyVersion", 1); put("activityId", activity.id); put("questionId", questionId?.let(::JsonPrimitive) ?: JsonNull)
            put("evidence", evidence)
        }.toString()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return RevisionSuggestion(activity.id, questionId, days, reason, fingerprint, observations, streak, assistantRatings, quizAttemptNumber = (activity.data as? ActivityData.Quiz)?.attemptNumber)
    }

    /** Focused activity first; cap keeps the extra provider context bounded. Private recall text is never used. */
    fun forWorkspace(state: Workspace, catalog: SourceCatalog?): List<RevisionSuggestion> = state.activities.values
        .sortedWith(compareBy<Activity> { if (it.id == state.context.focusedActivityId) 0 else 1 }.thenBy { it.id })
        .flatMap { activity ->
            val targets = (activity.data as? ActivityData.Quiz)?.questions?.map { it.id } ?: listOf(null)
            targets.mapNotNull { question ->
                val target = RevisionTargets.resolve(state, activity.id, question)
                if (target != null && catalog?.contains(target.source) == true) forActivity(activity, question) else null
            }
        }.take(200)
}
