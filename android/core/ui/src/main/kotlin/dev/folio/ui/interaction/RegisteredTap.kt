package dev.folio.ui.interaction

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.folio.model.ui.GestureId
import dev.folio.model.workspace.InteractionLock
import dev.folio.runtime.gesture.GestureRegistry

fun Modifier.registeredTap(label: String, lock: InteractionLock = InteractionLock.NONE,
    onClick: () -> Unit) = clickable(enabled = GestureRegistry.allowed(GestureId.TAP, lock),
    onClickLabel = label, role = Role.Button, onClick = onClick)
