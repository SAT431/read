package dev.folio.model.activity

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable data class RevisionCompletion(val completedAtEpochMillis: Long, val byAssistant: Boolean = false,
    val source: SourceAnchor? = null, val prompt: String? = null)

/** User-selected review timing and explicit completion, separate from assessment answers. */
@Serializable
data class RevisionItem(val id: String, val activityId: String, val questionId: String?,
    val source: SourceAnchor, val prompt: String, val dueAtEpochMillis: Long,
    val createdAtEpochMillis: Long, val updatedAtEpochMillis: Long, val version: Int = 1,
    val done: Boolean = false, val scheduledByAssistant: Boolean = false,
    val completions: List<RevisionCompletion> = emptyList(), val suggestion: RevisionSuggestion? = null)
