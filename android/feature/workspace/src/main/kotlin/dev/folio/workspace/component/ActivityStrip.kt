package dev.folio.workspace.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.validation.ActivityValidator

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityStrip(state: Workspace, onCommands: (List<WorkspaceCommand>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.activities.values.forEach { activity ->
            FolioActionButton("${ActivityValidator.kind(activity.data).name.lowercase()} · ${activity.status.name.lowercase()}", {
                onCommands(listOf(WorkspaceCommand.ResumeActivity(activity.id)))
            })
        }
    }
}
