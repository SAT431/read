package dev.folio.library.subject

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.StudyChapter
import dev.folio.ui.control.semanticControl

@Composable
internal fun ChapterRow(chapter: StudyChapter, pdfCount: Int, dispatch: (AppCommand) -> Unit) {
    val command = AppCommand.OpenChapterLibrary(chapter.id)
    OutlinedCard({ dispatch(command) }, Modifier.fillMaxWidth().semanticControl("chapter-${chapter.id}", command)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(chapter.title, style = MaterialTheme.typography.titleMedium)
            Text("Syllabus unit ${chapter.syllabusUnit} · $pdfCount PDFs${if (chapter.sharedBiology) " · shared Biology" else ""}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
