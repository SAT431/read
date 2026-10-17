package dev.folio.library.subject

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
import dev.folio.model.port.CurriculumCatalog

@Composable
fun SubjectScreen(subject: SubjectId, query: String, catalog: CurriculumCatalog, pdfs: List<LibraryPdf>, dispatch: (AppCommand) -> Unit) {
    val chapters = catalog.chapters().filter { it.subject == subject && it.title.contains(query, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp).testTag("subject-chapters"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(catalog.subjects().first { it.id == subject }.title, style = MaterialTheme.typography.headlineLarge)
            Text("${catalog.baseline} · open any chapter library") }
        item { ChapterSearch(query, dispatch) }
        if (chapters.isEmpty()) item { Text("No matching chapters. Try another search.") }
        items(chapters, key = { it.id }) { chapter -> ChapterRow(chapter, pdfs.count { it.chapterId == chapter.id }, dispatch) }
    }
}
