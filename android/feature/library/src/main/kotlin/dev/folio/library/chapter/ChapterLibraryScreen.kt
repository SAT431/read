package dev.folio.library.chapter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.*
import dev.folio.ui.control.SemanticButton

@Composable
fun ChapterLibraryScreen(chapter: StudyChapter, pdfs: List<LibraryPdf>, importing: Boolean, dispatch: (AppCommand) -> Unit) {
    val entries = pdfs.filter { it.chapterId == chapter.id }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp).testTag("chapter-library"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("${chapter.subject.name.lowercase()} / CHAPTER LIBRARY", style = MaterialTheme.typography.labelSmall)
            Text(chapter.title, style = MaterialTheme.typography.headlineLarge) }
        item { SemanticButton("import-${chapter.id}", if (importing) "Importing PDF…" else "Add PDF to this chapter",
            AppCommand.RequestPdfImport(chapter.id), dispatch, enabled = !importing) }
        if (entries.isEmpty()) item { EmptyLibrary(chapter.id, dispatch) }
        items(entries, key = { it.id }) { PdfCard(it, dispatch) }
    }
}
