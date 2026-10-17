package dev.folio.runtime.practice

import dev.folio.model.activity.RevisionItem

object RevisionQueueValidator {
    fun validate(items: Map<String, RevisionItem>) {
        require(items.size <= 1000)
        items.forEach { (id, item) ->
            require(id == item.id && id == RevisionTargets.id(item.activityId, item.questionId))
            require(item.activityId.isNotBlank() && item.activityId.length <= 100 && (item.questionId?.length ?: 0) <= 200)
            require(item.source.documentId.isNotBlank() && item.source.documentId.length <= 200 && item.source.pageIndex >= 0)
            require(item.source.elementId?.let { it.length in 1..200 } != false)
            require(item.prompt.isNotBlank() && item.prompt.length <= 20000)
            require(item.version >= 1 && item.dueAtEpochMillis >= 0 && item.createdAtEpochMillis >= 0 && item.updatedAtEpochMillis >= 0)
            require(item.completions.size <= 100 && item.completions.all { it.completedAtEpochMillis >= 0 })
            item.suggestion?.let { suggestion ->
                require(suggestion.activityId == item.activityId && suggestion.questionId == item.questionId)
                require(suggestion.quizAttemptNumber?.let { it in 1..101 } != false)
                require(suggestion.policyVersion == 1 && suggestion.delayDays in setOf(1, 3, 7, 14, 30, 60, 90))
                require(suggestion.evidenceFingerprint.matches(Regex("[a-f0-9]{64}")))
                require(suggestion.observations in 1..101 && suggestion.recalledStreak in 0..suggestion.observations)
            }
            require(!item.done || item.completions.isNotEmpty())
            item.completions.forEach { report ->
                require((report.source == null) == (report.prompt == null))
                report.source?.let { require(it.documentId.length in 1..200 && it.pageIndex >= 0 && it.elementId?.let { id -> id.length in 1..200 } != false) }
                report.prompt?.let { require(it.isNotBlank() && it.length <= 20000) }
            }
        }
        require(items.values.sumOf { it.prompt.length.toLong() } <= 1_000_000)
        require(items.values.sumOf { it.completions.size } <= 10000)
        require(items.values.sumOf { item -> item.completions.sumOf { it.prompt?.length?.toLong() ?: 0L } } <= 1_000_000) {
            "Revision completion history storage limit reached"
        }
    }
}
