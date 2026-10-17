package dev.folio.model.ui

import kotlinx.serialization.Serializable

@Serializable
data class ComponentContract(
    val kind: ComponentKind,
    val title: String,
    val minimumWidthDp: Int,
    val preferredWidthDp: Int,
    val presentations: Set<Presentation>,
    val actions: Set<ComponentAction>,
    val gestures: Set<GestureId>,
    val motions: Set<MotionId>,
    val persistedState: String,
    val accessibility: String,
)
