package dev.folio.runtime.gesture

import dev.folio.model.ui.GestureId
import dev.folio.model.workspace.InteractionLock

data class GestureContract(val id: GestureId, val priority: Int, val blockedBy: Set<InteractionLock>,
    val description: String)

object GestureRegistry {
    val contracts = listOf(
        GestureContract(GestureId.TAP, 10, setOf(InteractionLock.TEXT_SELECTION, InteractionLock.DRAGGING), "Activate a registered action"),
        GestureContract(GestureId.LONG_PRESS, 20, setOf(InteractionLock.WRITING), "Open contextual selection"),
        GestureContract(GestureId.NATIVE_SELECTION, 40, emptySet(), "Platform text selection takes priority"),
        GestureContract(GestureId.SCROLL, 30, setOf(InteractionLock.DRAGGING), "Native scroll owns the gesture"),
        GestureContract(GestureId.TRANSFORM, 50, setOf(InteractionLock.WRITING, InteractionLock.TEXT_SELECTION), "Bounded diagram pinch and pan"),
    ).associateBy { it.id }
    fun allowed(id: GestureId, lock: InteractionLock) = lock !in contracts.getValue(id).blockedBy
}
