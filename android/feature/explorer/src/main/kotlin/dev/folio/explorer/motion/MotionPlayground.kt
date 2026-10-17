package dev.folio.explorer.motion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.*
import dev.folio.model.ui.MotionId
import dev.folio.runtime.motion.MotionRegistry
import dev.folio.ui.motion.RegisteredMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MotionPlayground(reducedMotion: Boolean) {
    var selected by remember { mutableStateOf(MotionId.FADE) }
    var generation by remember { mutableIntStateOf(0) }
    Text("Motion playground")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MotionId.entries.forEach { id -> FolioActionButton(id.name.lowercase(), { selected = id; generation++ }) }
    }
    key(generation) {
        RegisteredMotion(selected, reducedMotion) {
            FolioSurface { Text("${selected.name} · ${MotionRegistry.resolve(selected, reducedMotion).durationMs}ms") }
        }
    }
}
