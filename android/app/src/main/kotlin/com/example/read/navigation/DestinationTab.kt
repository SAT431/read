package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import dev.folio.model.command.AppCommand
import dev.folio.model.workspace.AppDestination
import dev.folio.ui.control.semanticControl
import dev.folio.ui.icon.*

@Composable
internal fun DestinationTab(destination: AppDestination, label: String, icon: IconId, selected: Boolean, dispatch: (AppCommand) -> Unit) {
    val command = AppCommand.Navigate(destination)
    val active = selected
    FilledTonalButton({ dispatch(command) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semanticControl("tab-${destination.name.lowercase()}", command).semantics { this.selected = active },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) {
        FolioIcon(icon, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f))
    }
}
