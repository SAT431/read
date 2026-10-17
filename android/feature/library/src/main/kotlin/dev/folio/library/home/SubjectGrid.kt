package dev.folio.library.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.library.component.SubjectCard
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.CurriculumCatalog

@Composable
internal fun SubjectGrid(catalog: CurriculumCatalog, pdfs: List<LibraryPdf>, dispatch: (AppCommand) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val columnCount = if (fontScale > 1.4f) 1 else if (maxWidth >= 1100.dp) 4 else if (maxWidth >= 440.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            catalog.subjects().chunked(columnCount).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { subject ->
                    val chapters = catalog.chapters().filter { it.subject == subject.id }
                    SubjectCard(subject, chapters.size, pdfs.count { pdf -> chapters.any { it.id == pdf.chapterId } }, dispatch, Modifier.weight(1f))
                } }
            }
        }
    }
}
