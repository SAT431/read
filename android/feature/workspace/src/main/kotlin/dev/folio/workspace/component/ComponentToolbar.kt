package dev.folio.workspace.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.ComponentInstance
import dev.folio.runtime.interaction.LearningInteraction

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComponentToolbar(component: ComponentInstance, title: String,
    onCommands: (List<WorkspaceCommand>) -> Unit, onInteraction: (LearningInteraction) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))) {
        FolioActionButton(if (component.pinned) "Unpin" else "Pin", {
            onInteraction(LearningInteraction.PinComponent(component.id, !component.pinned))
        })
        FolioActionButton("Focus", { onCommands(listOf(WorkspaceCommand.FocusActivity(component.activityId))) })
        FolioActionButton("Hide", { onCommands(listOf(WorkspaceCommand.HideComponent(component.id))) })
    }
}
