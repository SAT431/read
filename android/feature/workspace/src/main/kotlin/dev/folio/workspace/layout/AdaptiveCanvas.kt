package dev.folio.workspace.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.ui.ComponentInstance
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.layout.LayoutSolver

@Composable
internal fun AdaptiveCanvas(state: Workspace, solver: LayoutSolver,
    render: @Composable (ComponentInstance) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val plan = remember(state.components, state.activities, state.focusedComponentId, maxWidth) {
            solver.solve(state, maxWidth.value.coerceAtLeast(1f))
        }
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            plan.columns.forEach { column ->
                Column(Modifier.width(column.widthDp.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    column.groups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.components.forEach { component -> key(component.id) { render(component) } }
                        }
                    }
                }
            }
        }
    }
}
