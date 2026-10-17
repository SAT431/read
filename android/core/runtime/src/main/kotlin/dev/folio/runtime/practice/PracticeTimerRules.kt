package dev.folio.runtime.practice

import dev.folio.model.activity.*

object PracticeTimerRules {
    const val MAX_ELAPSED = 365L * 86_400_000L
    const val MAX_LIMIT_SECONDS = 86_400
    const val MAX_VERSION = 1_000_000
    fun validate(timer: PracticeTimer, archived: Boolean = false) {
        require(timer.elapsedMillis in 0..MAX_ELAPSED && timer.version in 0..MAX_VERSION)
        require(timer.limitMillis?.let { it in 1000..MAX_LIMIT_SECONDS * 1000L && it % 1000L == 0L } != false)
        require(timer.status != TimerStatus.IDLE || timer.elapsedMillis == 0L)
        if (timer.status == TimerStatus.EXPIRED) require(timer.limitMillis?.let { timer.elapsedMillis >= it } == true)
        if (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED)
            require(timer.limitMillis?.let { timer.elapsedMillis < it } != false)
        require(!archived || timer.status != TimerStatus.RUNNING)
    }
    fun pause(timer: PracticeTimer, reason: TimerChange): PracticeTimer = if (timer.status != TimerStatus.RUNNING) timer else
        timer.copy(status = TimerStatus.PAUSED, version = (timer.version + 1).coerceAtMost(MAX_VERSION), lastChange = reason, changedByAssistant = false)
    fun advance(timer: PracticeTimer, deltaMillis: Long): PracticeTimer {
        if (timer.status != TimerStatus.RUNNING || deltaMillis <= 0) return timer
        val added = timer.elapsedMillis + deltaMillis.coerceAtMost(MAX_ELAPSED - timer.elapsedMillis)
        val limit = timer.limitMillis
        return when {
            limit != null && added >= limit -> timer.copy(elapsedMillis = limit, status = TimerStatus.EXPIRED,
                version = (timer.version + 1).coerceAtMost(MAX_VERSION), lastChange = TimerChange.EXPIRED, changedByAssistant = false)
            added >= MAX_ELAPSED -> pause(timer.copy(elapsedMillis = MAX_ELAPSED), TimerChange.TIME_BOUND)
            else -> timer.copy(elapsedMillis = added)
        }
    }
}
