package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable enum class TimerStatus { IDLE, RUNNING, PAUSED, EXPIRED }
@Serializable enum class TimerChange {
    NONE, STARTED, PAUSED, RESET, LIMIT_SET, SUSPENDED, HIDDEN, REVIEWING_HISTORY,
    LEFT_PRACTICE, BACKGROUNDED, RECOVERED, EXPIRED, CLOCK_INTERRUPTED, TIME_BOUND, ARCHIVED
}

/** Counted practice time only. Runtime monotonic anchors are never persisted or model-supplied. */
@Serializable
data class PracticeTimer(val elapsedMillis: Long = 0, val limitMillis: Long? = null,
    val status: TimerStatus = TimerStatus.IDLE, val version: Int = 0,
    val lastChange: TimerChange = TimerChange.NONE, val changedByAssistant: Boolean = false,
    val detailsExpanded: Boolean = false)
