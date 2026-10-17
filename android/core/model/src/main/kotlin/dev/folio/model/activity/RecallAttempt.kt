package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable enum class RecallRating { FORGOT, PARTIAL, RECALLED }

/** Explicit reported recall, not an automatically inferred mastery score. */
@Serializable
data class RecallAttempt(val response: String, val rating: RecallRating, val ratedAtEpochMillis: Long,
    val responseByAssistant: Boolean = false, val ratingByAssistant: Boolean = false)
