package dev.folio.designsystem.primitive

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.theme.LocalReducedMotion

@Composable
fun FolioActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, prominent: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (pressed && !reduced) .975f else 1f,
        if (reduced) snap() else spring(stiffness = 550f), label = "button-press")
    val colors = MaterialTheme.colorScheme
    val base = if (prominent) colors.primary else colors.surfaceContainer
    val ink = if (prominent) colors.onPrimary else colors.onSurface
    Button(onClick, modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }, enabled,
        shape = RoundedCornerShape(14.dp), interactionSource = interactions, contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, if (prominent) colors.primary else colors.outlineVariant),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ink,
            disabledContainerColor = colors.surfaceContainer, disabledContentColor = colors.onSurface.copy(alpha = .38f))) {
        Box(Modifier.background(if (enabled) Brush.verticalGradient(listOf(lerp(base, Color.White, .09f), base))
            else Brush.verticalGradient(listOf(colors.surfaceContainer, colors.surfaceContainer)))
            .padding(horizontal = 18.dp, vertical = 12.dp)) { Text(label, style = MaterialTheme.typography.labelLarge) }
    }
}
