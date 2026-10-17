package dev.folio.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.explorer.catalog.ContractCard
import dev.folio.explorer.catalog.AppActionPanel
import dev.folio.explorer.motion.MotionPlayground
import dev.folio.explorer.trace.TracePanel
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.command.CommandTrace
import dev.folio.runtime.gesture.GestureRegistry
import dev.folio.runtime.interaction.InteractionRegistry
import dev.folio.runtime.registry.ComponentRegistry

@Composable
fun ExplorerScreen(registry: ComponentRegistry, traces: List<CommandTrace>, reducedMotion: Boolean,
    onAdd: (ComponentKind) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp).testTag("component-explorer"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Prebuilt interface universe", style = MaterialTheme.typography.headlineLarge)
            Text("Inspectable contracts and working representative renderers · fixture resources") }
        item { MotionPlayground(reducedMotion) }
        item { AppActionPanel() }
        items(registry.all(), key = { it.kind }) { ContractCard(it) { onAdd(it.kind) } }
        item { Text("Gesture policy"); GestureRegistry.contracts.values.forEach { Text("${it.id}: ${it.description} · priority ${it.priority}") } }
        item { Text("Local interactions: ${InteractionRegistry.contracts.keys.joinToString()}") }
        item { TracePanel(traces) }
    }
}
