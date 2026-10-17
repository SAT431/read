package dev.folio.model.activity

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityStatus { ACTIVE, SUSPENDED, COMPLETED }

@Serializable
data class Activity(val id: String, val source: SourceAnchor, val data: ActivityData,
    val status: ActivityStatus = ActivityStatus.ACTIVE, val originatingActivityId: String? = null)
