package dev.folio.library.search

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.search.*
import dev.folio.ui.control.SemanticButton

@Composable
internal fun SearchResultCard(result: SearchResult, dispatch: (AppCommand) -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(result.target.kind.name.lowercase(), style = MaterialTheme.typography.labelMedium)
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            if (result.isFixture) Text("Labelled sample content", style = MaterialTheme.typography.labelSmall)
            Text(result.detail, style = MaterialTheme.typography.bodySmall)
            if (result.excerpt != result.title) Text(result.excerpt)
            if (!result.canOpen) Text("This saved source changed or is unavailable. The saved record is retained.")
            SemanticButton("search-open-${result.fingerprint}", if (result.target.kind == SearchKind.NOTE) "View saved note" else "Open result",
                AppCommand.OpenSearchResult(result.target, result.fingerprint), dispatch, enabled = result.canOpen)
        }
    }
}
