package dev.folio.library.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.AppDestination
import dev.folio.ui.control.semanticControl

@Composable
internal fun HomeStudyCard(pdfs: List<LibraryPdf>, dispatch: (AppCommand) -> Unit) {
    val command = AppCommand.Navigate(AppDestination.ASSISTANT)
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF252E2D), contentColor = Color(0xFFF7F7EF)) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF253E36), Color(0xFF292D35))))) {
            StudyOrbitArt(Modifier.matchParentSize())
            val wide = maxWidth >= 640.dp && LocalDensity.current.fontScale <= 1.4f
            val words: @Composable (Modifier) -> Unit = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("STUDY STUDIO", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBED7C9))
                Text("Pick a page. Go deeper.", style = MaterialTheme.typography.headlineMedium)
                Text(if (pdfs.isEmpty()) "Add your first PDF in a subject below." else "Your PDFs and questions, connected.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD7DFDB))
            } }
            val action: @Composable () -> Unit = {
                FolioActionButton("Ask your assistant  ↗", { dispatch(command) },
                    Modifier.semanticControl("home-assistant", command), prominent = true)
            }
            if (wide) Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) { words(Modifier.weight(1f)); action() }
            else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { words(Modifier); action() }
        }
    }
}
