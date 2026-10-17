package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.workspace.AppDestination
import dev.folio.ui.control.SemanticButton
import dev.folio.ui.control.semanticControl
import dev.folio.ui.icon.IconId

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppTopBar(destination: AppDestination, dispatch: (AppCommand) -> Unit, wide: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (destination != AppDestination.HOME) TextButton({ dispatch(AppCommand.Back) },
            Modifier.semanticControl("app-back", AppCommand.Back)) { Text("← Back") }
        Text(if (!wide && destination == AppDestination.HOME) "folio /" else destination.name.lowercase().replaceFirstChar(Char::titlecase),
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (!wide) Box {
            TextButton({ menu = true }, Modifier.testTag("app-more")) { Text("More ···") }
            DropdownMenu(menu, { menu = false }) {
                secondaryNavigation.forEach { item ->
                    val command = AppCommand.Navigate(item.destination)
                    DropdownMenuItem(text = { Text(item.label) }, onClick = { menu = false; dispatch(command) },
                        modifier = Modifier.semanticControl("tab-${item.destination.name.lowercase()}", command))
                }
            }
        }
    }
}
