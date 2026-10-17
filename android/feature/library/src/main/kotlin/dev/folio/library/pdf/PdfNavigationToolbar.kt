package dev.folio.library.pdf

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.AppNavigation
import dev.folio.ui.control.SemanticButton
import dev.folio.ui.control.semanticControl

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfNavigationToolbar(pdf: LibraryPdf, navigation: AppNavigation, dispatch: (AppCommand) -> Unit) {
    var tools by remember { mutableStateOf(false) }
    var jump by rememberSaveable(pdf.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        TextButton({dispatch(AppCommand.Back)},Modifier.semanticControl("app-back",AppCommand.Back)) {Text("← Back")}
        Text(pdf.document.title,Modifier.weight(1f).padding(horizontal=6.dp),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
        TextButton({dispatch(AppCommand.PreviousPdfPage)},Modifier.semanticControl("pdf-previous",AppCommand.PreviousPdfPage).semantics{contentDescription="Previous PDF page"},enabled=navigation.pdfPageIndex>0) {Text("‹")}
        TextButton({jump=true},Modifier.testTag("pdf-page-counter").semantics{contentDescription="Jump to PDF page"}) {Text("${navigation.pdfPageIndex+1} / ${pdf.document.pageCount}")}
        TextButton({dispatch(AppCommand.NextPdfPage)},Modifier.semanticControl("pdf-next",AppCommand.NextPdfPage).semantics{contentDescription="Next PDF page"},enabled=navigation.pdfPageIndex<pdf.document.pageCount-1) {Text("›")}
        Box {
            TextButton({tools=true},Modifier.testTag("pdf-tools").semantics{contentDescription="PDF tools"}) {Text("⋯")}
            DropdownMenu(tools, { tools = false }) {
                DropdownMenuItem({Text("Study with AI")},{tools=false;dispatch(AppCommand.StudyPdf(pdf.id))},Modifier.semanticControl("pdf-study",AppCommand.StudyPdf(pdf.id)))
                DropdownMenuItem({Text("Extract this page")},{tools=false;dispatch(AppCommand.EngineerPage(pdf.document.id,navigation.pdfPageIndex))},Modifier.semanticControl("pdf-process",AppCommand.EngineerPage(pdf.document.id,navigation.pdfPageIndex)))
                DropdownMenuItem({Text("Compare extraction")},{tools=false;dispatch(AppCommand.ReviewPage(pdf.document.id,navigation.pdfPageIndex))},Modifier.semanticControl("pdf-review",AppCommand.ReviewPage(pdf.document.id,navigation.pdfPageIndex)))
                listOf(Triple("pdf-zoom-in", "Zoom +", AppCommand.PdfZoom((navigation.pdfZoom + .25f).coerceAtMost(4f))),
                    Triple("pdf-zoom-out", "Zoom −", AppCommand.PdfZoom((navigation.pdfZoom - .25f).coerceAtLeast(.5f))),
                    Triple("pdf-zoom-reset", "Reset view", AppCommand.PdfTransform(1f, 0f, 0f))).forEach { (id, label, command) ->
                    DropdownMenuItem({ Text(label) }, { tools = false; dispatch(command) }, modifier = Modifier.semanticControl(id, command))
                }
                DropdownMenuItem({ Text("Jump to page") }, { tools = false; jump = true }, modifier = Modifier.testTag("pdf-jump"))
            }
        }
    }
    if (jump) AlertDialog(onDismissRequest = { jump = false }, title = { Text("Jump to page") }, text = {
        PdfPageSelector(pdf.id, navigation.pdfPageIndex, pdf.document.pageCount) { command -> jump = false; dispatch(command) }
    }, confirmButton = { TextButton({ jump = false }) { Text("Close") } })
}
