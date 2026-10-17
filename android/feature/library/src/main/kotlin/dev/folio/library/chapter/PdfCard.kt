package dev.folio.library.chapter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioSurface
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.ui.control.SemanticButton
import dev.folio.ui.icon.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfCard(pdf: LibraryPdf, dispatch: (AppCommand) -> Unit) {
    FolioSurface {
        FolioIcon(IconId.PDF)
        Text(pdf.document.title, style = MaterialTheme.typography.titleMedium)
        Text("${pdf.document.pageCount} pages · saved on this device")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SemanticButton("open-${pdf.id}", "Open original PDF", AppCommand.OpenPdf(pdf.id), dispatch)
            SemanticButton("study-${pdf.id}", "Study with AI", AppCommand.StudyPdf(pdf.id), dispatch)
        }
    }
}
