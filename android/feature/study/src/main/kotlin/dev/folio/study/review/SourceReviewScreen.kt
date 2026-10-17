package dev.folio.study.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.SourcePage
import dev.folio.model.source.SourceCorrection
import dev.folio.model.source.SourceCorrectionDraft
import dev.folio.model.source.SourceAnchor
import dev.folio.model.source.SourceRegion
import dev.folio.runtime.engineering.SourceRegions
import dev.folio.model.workspace.AppNavigation
import dev.folio.ui.control.SemanticButton
import kotlinx.serialization.json.JsonObject

/** Original viewport is injected by the app; feature modules remain independent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceReviewScreen(pdf: LibraryPdf, navigation: AppNavigation, page: SourcePage?, report: JsonObject?, media: MediaRepository,
    dispatch: (AppCommand) -> Unit, corrections: Map<String, SourceCorrection> = emptyMap(),
    drafts: Map<String, SourceCorrectionDraft> = emptyMap(), original: @Composable (SourceRegion?) -> Unit) {
    val reconstruction = page?.takeIf { it.documentId == pdf.document.id && it.pageIndex == navigation.pdfPageIndex }
    val comparison = if (page != null && reconstruction == null) null else report
    val region = navigation.reviewElementId?.let { SourceRegions.resolve(reconstruction,
        SourceAnchor(pdf.document.id, navigation.pdfPageIndex, it)) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Compare with original · page ${navigation.pdfPageIndex + 1} of ${pdf.document.pageCount}", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SemanticButton("review-previous", "Previous page", AppCommand.PreviousPdfPage, dispatch, enabled = navigation.pdfPageIndex > 0)
            SemanticButton("review-next", "Next page", AppCommand.NextPdfPage, dispatch, enabled = navigation.pdfPageIndex < pdf.document.pageCount - 1)
            SemanticButton("review-close", "Open original", AppCommand.OpenPdf(pdf.id), dispatch)
            SemanticButton("review-study", "Study this page", AppCommand.StudyPdf(pdf.id), dispatch)
            SemanticButton("review-reset", "Reset view", AppCommand.PdfTransform(1f, 0f, 0f), dispatch)
            if (navigation.reviewElementId != null)
                SemanticButton("review-clear-region", "Clear region", AppCommand.ReviewRegion(null), dispatch)
        }
        if (navigation.reviewElementId != null) Text(if (region == null) "Selected region is unavailable in the current reconstruction."
            else "Highlighted original region: ${navigation.reviewElementId}. Coordinates are machine supplied; check the original.", style = MaterialTheme.typography.labelMedium)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 760.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) { Text("Original PDF"); Box(Modifier.weight(1f).testTag("review-original")) { original(region) } }
                Box(Modifier.weight(1f).fillMaxHeight()) { key(pdf.id, navigation.pdfPageIndex) { ReconstructionReviewPane(reconstruction, comparison, media, pdf.document.checksum, corrections, drafts, navigation.reviewElementId, dispatch) } }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Original PDF")
                Box(Modifier.weight(1f).fillMaxWidth().testTag("review-original")) { original(region) }
                Box(Modifier.weight(1f).fillMaxWidth()) { key(pdf.id, navigation.pdfPageIndex) { ReconstructionReviewPane(reconstruction, comparison, media, pdf.document.checksum, corrections, drafts, navigation.reviewElementId, dispatch) } }
            }
        }
    }
}
