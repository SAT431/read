package dev.folio.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.folio.model.ui.MotionId
import dev.folio.runtime.motion.MotionRegistry

/** Runs only built-in Compose transitions. Changing a preset never resets learning state. */
@Composable
fun RegisteredMotion(id: MotionId, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val preset = MotionRegistry.resolve(id, reducedMotion)
    var entered by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { entered = true }
    val enter = fadeIn(tween(preset.durationMs)) + if (preset.slides)
        slideInVertically(tween(preset.durationMs)) { it / 12 } else EnterTransition.None
    AnimatedVisibility(visible = entered, enter = enter, exit = fadeOut(tween(preset.durationMs))) {
        Column(if (preset.resizes) Modifier.animateContentSize(tween(preset.durationMs)) else Modifier) { content() }
    }
}
