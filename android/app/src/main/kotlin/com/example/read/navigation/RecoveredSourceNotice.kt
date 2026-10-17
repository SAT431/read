package com.example.read.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.source.SavedSourceReferences

@Composable
fun RecoveredSourceNotice(state: Workspace, recovered: List<SourceAnchor>, catalog: SourceCatalog, sourceRevision: Long) {
    val missing = remember(state, recovered, sourceRevision) {
        val current = SavedSourceReferences.all(state).toSet()
        recovered.any { it in current && !SavedSourceReferences.available(it, catalog) }
    }
    if (missing) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Text("Your saved study work was restored. Some source text is currently unavailable.",
            Modifier.padding(12.dp).testTag("source-recovery-notice"), color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium)
    }
}
