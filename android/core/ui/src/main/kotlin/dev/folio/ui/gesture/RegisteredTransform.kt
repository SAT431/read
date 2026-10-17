package dev.folio.ui.gesture

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.folio.model.ui.GestureId
import dev.folio.model.workspace.InteractionLock
import dev.folio.runtime.gesture.GestureRegistry

@Composable
fun Modifier.registeredTransform(lock: InteractionLock = InteractionLock.NONE,
    onTransform: (zoomDelta: Float, panX: Float, panY: Float) -> Unit): Modifier {
    val state = rememberTransformableState { zoom, pan, _ ->
        if (GestureRegistry.allowed(GestureId.TRANSFORM, lock)) onTransform(zoom, pan.x, pan.y)
    }
    return transformable(state, enabled = GestureRegistry.allowed(GestureId.TRANSFORM, lock))
}
