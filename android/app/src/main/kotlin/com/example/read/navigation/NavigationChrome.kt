package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.workspace.AppDestination
import dev.folio.ui.control.semanticControl
import dev.folio.ui.icon.FolioIcon

@Composable
internal fun NavigationSidebar(destination: AppDestination, dispatch: (AppCommand) -> Unit) {
    Surface(Modifier.width(190.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("folio /", Modifier.padding(12.dp), style = MaterialTheme.typography.headlineMedium)
            Text("YOUR STUDY SPACE", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            primaryNavigation.forEach { item -> DestinationTab(item.destination, item.label, item.icon,
                destination.navigationGroup() == item.destination, dispatch) }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            secondaryNavigation.forEach { item -> DestinationTab(item.destination, item.label, item.icon,
                destination == item.destination, dispatch) }
        }
    }
}

@Composable
internal fun CompactNavigation(destination: AppDestination, dispatch: (AppCommand) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
        primaryNavigation.forEach { item ->
            val command = AppCommand.Navigate(item.destination)
            NavigationBarItem(destination.navigationGroup() == item.destination, { dispatch(command) },
                icon = { FolioIcon(item.icon, Modifier.size(24.dp)) }, label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.semanticControl("tab-${item.destination.name.lowercase()}", command))
        }
    }
}
