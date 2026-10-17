package dev.folio.ui.interaction

import androidx.compose.runtime.*
import dev.folio.runtime.interaction.InteractionGate
import java.util.UUID

val LocalInteractionGate = staticCompositionLocalOf<InteractionGate?> { null }

/** Call the returned function inside editor handlers to protect before recomposition. */
@Composable
fun rememberWritingProtection(active: Boolean): (Boolean) -> Unit {
    val gate = LocalInteractionGate.current
    val owner = remember { UUID.randomUUID().toString() }
    val update = remember(gate, owner) { { value: Boolean -> gate?.set(owner, value); Unit } }
    SideEffect { update(active) }
    DisposableEffect(gate, owner) { onDispose { update(false) } }
    return update
}
