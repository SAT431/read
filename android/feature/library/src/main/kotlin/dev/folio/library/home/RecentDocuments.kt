package dev.folio.library.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.ui.control.semanticControl
import dev.folio.ui.icon.*

@Composable
internal fun RecentDocuments(pdfs: List<LibraryPdf>, dispatch: (AppCommand) -> Unit) {
    if (pdfs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recently added", style = MaterialTheme.typography.titleMedium)
        pdfs.sortedByDescending { it.importedAt }.take(3).forEach { pdf ->
            val command = AppCommand.OpenPdf(pdf.id)
            Surface(onClick = { dispatch(command) }, modifier = Modifier.fillMaxWidth().semanticControl("recent-${pdf.id}", command),
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FolioIcon(IconId.PDF, Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pdf.document.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("${pdf.document.pageCount} pages · on this device", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
