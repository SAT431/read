package dev.folio.study.document

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.folio.model.command.AppCommand
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.SourceElementKind
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.study.reader.*
import dev.folio.ui.control.SemanticButton

@Composable
internal fun StudyReadingContent(row:StudyReadingRow.Content,plan:DocumentStudyPlan,media:MediaRepository,
    textSize:Int,dispatch:(AppCommand)->Unit) {
    val block=row.block
    when(block.source.kind) {
        SourceElementKind.HEADING -> Text(block.displayedText,style=MaterialTheme.typography.headlineMedium)
        SourceElementKind.FIGURE -> Column {
            OriginalFigureBlock(block.source,media)
            SemanticButton("study-enlarge-${row.section.documentId}-${block.source.id}","Enlarge original",
                AppCommand.HarnessInspectPage(row.section.pdfId,row.section.pageIndex),dispatch)
        }
        else -> SelectionContainer { SentenceText(block,textSize,plan.sentenceId,emptyList()) { sentence ->
            dispatch(AppCommand.StudySentence(plan.pdfId,plan.version,sentence))
        } }
    }
}
