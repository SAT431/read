package dev.folio.model.ui

import kotlinx.serialization.Serializable

@Serializable
data class ComponentInstance(val id: String, val activityId: String, val kind: ComponentKind,
    val visible: Boolean = true, val pinned: Boolean = false, val priority: Int = 5,
    val presentation: Presentation = Presentation.SURFACE, val motion: MotionId = MotionId.FADE)
