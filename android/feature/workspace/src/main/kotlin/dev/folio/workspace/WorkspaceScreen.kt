package dev.folio.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.port.MediaRepository
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.layout.LayoutSolver
import dev.folio.runtime.registry.ComponentRegistry
import dev.folio.ui.component.RendererRegistry
import dev.folio.workspace.component.*
import dev.folio.workspace.layout.AdaptiveCanvas

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(state: Workspace, catalog: SourceCatalog, media: MediaRepository, capabilities: ComponentRegistry,
    renderers: RendererRegistry, solver: LayoutSolver, onCommands: (List<WorkspaceCommand>) -> Unit,
    onInteraction: (LearningInteraction) -> Unit, onUndo: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val fixture = state.context.source?.let { catalog.page(it.documentId, it.pageIndex)?.isFixture } == true
        Text(if (fixture) "Sample workspace · demonstration content" else "Study workspace")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolioActionButton("Previous page", { onCommands(listOf(WorkspaceCommand.PreviousPage)) })
            FolioActionButton("Next page", { onCommands(listOf(WorkspaceCommand.NextPage)) })
            FolioActionButton("Undo layout", onUndo)
        }
        ActivityStrip(state, onCommands)
        AdaptiveCanvas(state, solver) { component ->
            ComponentHost(component, state, catalog, media, capabilities, renderers, onCommands, onInteraction)
        }
    }
}
