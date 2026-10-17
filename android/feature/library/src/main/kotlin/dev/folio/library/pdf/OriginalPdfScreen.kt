package dev.folio.library.pdf

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.AppNavigation

@Composable
fun OriginalPdfScreen(pdf: LibraryPdf, navigation: AppNavigation, tools: PdfTools, media: MediaRepository, dispatch: (AppCommand) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        PdfNavigationToolbar(pdf, navigation, dispatch)
        Box(Modifier.weight(1f).fillMaxWidth()) { OriginalPdfPage(pdf, navigation, tools, media, dispatch) }
    }
}
