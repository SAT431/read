package dev.folio.ui.practice

import dev.folio.model.activity.ConfidenceLevel

fun confidenceLabel(level: ConfidenceLevel): String = when (level) {
    ConfidenceLevel.LOW -> "Unsure"
    ConfidenceLevel.MEDIUM -> "Somewhat sure"
    ConfidenceLevel.HIGH -> "Very sure"
}
