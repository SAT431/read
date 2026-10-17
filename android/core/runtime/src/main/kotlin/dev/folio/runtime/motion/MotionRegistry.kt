package dev.folio.runtime.motion

import dev.folio.model.ui.MotionId

data class MotionPreset(val id: MotionId, val durationMs: Int, val fades: Boolean,
    val slides: Boolean = false, val resizes: Boolean = false)

object MotionRegistry {
    val presets = listOf(
        MotionPreset(MotionId.FADE, 180, fades = true),
        MotionPreset(MotionId.EXPAND, 240, fades = true, resizes = true),
        MotionPreset(MotionId.SLIDE, 200, fades = true, slides = true),
        MotionPreset(MotionId.FOCUS, 120, fades = true),
    ).associateBy { it.id }
    fun resolve(id: MotionId, reducedMotion: Boolean) = presets.getValue(id).let {
        if (reducedMotion) it.copy(durationMs = 0, slides = false, resizes = false) else it
    }
}
