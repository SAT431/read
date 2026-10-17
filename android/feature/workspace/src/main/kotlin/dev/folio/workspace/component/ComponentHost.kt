package dev.folio.workspace.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import dev.folio.designsystem.primitive.FolioSurface
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.port.MediaRepository
import dev.folio.model.ui.ComponentInstance
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.registry.ComponentRegistry
import dev.folio.ui.component.*
import dev.folio.ui.motion.RegisteredMotion

@Composable
internal fun ComponentHost(component: ComponentInstance, state: Workspace, catalog: SourceCatalog, media: MediaRepository,
    capabilities: ComponentRegistry, renderers: RendererRegistry, onCommands: (List<WorkspaceCommand>) -> Unit,
    onInteraction: (LearningInteraction) -> Unit) {
    val activity = state.activities[component.activityId] ?: return
    RegisteredMotion(component.motion, state.preferences.reducedMotion) {
        FolioSurface(Modifier.fillMaxWidth()) {
            ComponentToolbar(component, capabilities.require(component.kind).title, onCommands, onInteraction)
            renderers.Render(ComponentRenderContext(component, activity, catalog, media, state.preferences, onCommands, onInteraction, state.revisionItems))
        }
    }
}
