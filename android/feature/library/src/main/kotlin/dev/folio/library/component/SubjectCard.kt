package dev.folio.library.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.folio.model.command.AppCommand
import dev.folio.model.library.Subject
import dev.folio.ui.control.semanticControl
import dev.folio.ui.icon.*

@Composable
internal fun SubjectCard(subject: Subject, chapterCount: Int, pdfCount: Int, dispatch: (AppCommand) -> Unit,
    modifier: Modifier = Modifier) {
    val command = AppCommand.OpenSubject(subject.id)
    val accent = when (subject.id.name) {
        "PHYSICS" -> Color(0xFFBA9162); "CHEMISTRY" -> Color(0xFF9C8ABA)
        "BOTANY" -> Color(0xFF75A48B); else -> Color(0xFFBD8C8D)
    }
    OutlinedCard(onClick = { dispatch(command) }, modifier.semanticControl("subject-${subject.id.name.lowercase()}", command),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), color = lerp(MaterialTheme.colorScheme.surface, accent, .18f)) {
                    FolioIcon(IconId.valueOf(subject.id.name), Modifier.padding(5.dp))
                }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subject.title, style = MaterialTheme.typography.titleLarge)
                Text("$chapterCount chapters · $pdfCount PDFs", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
