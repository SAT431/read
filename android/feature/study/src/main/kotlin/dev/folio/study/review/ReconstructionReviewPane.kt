package dev.folio.study.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.*
import dev.folio.model.command.AppCommand
import dev.folio.study.reader.OriginalFigureBlock
import dev.folio.runtime.engineering.SourceRegions
import dev.folio.ui.control.SemanticButton
import kotlinx.serialization.json.JsonObject

@Composable
internal fun ReconstructionReviewPane(page: SourcePage?, report: JsonObject?, media: MediaRepository,
    checksum: String, corrections: Map<String, SourceCorrection>, drafts: Map<String, SourceCorrectionDraft>, selectedId: String?, dispatch: (AppCommand) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("reconstruction-review"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { FidelityDetails(report) }
        if (page == null) item { Text("This page has not been reconstructed. You can process it or keep reading the original.") }
        else {
            item { Text("Machine reconstruction · ${page.status.name.lowercase()}", style = MaterialTheme.typography.titleMedium) }
            items(page.elements.sortedBy { it.order }, key = { "element-${it.id}" }) { element ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(element.kind.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                        val source = SourceAnchor(page.documentId, page.pageIndex, element.id)
                        if (SourceRegions.resolve(page, source) != null)
                            SemanticButton("review-region-${element.id}", if (selectedId == element.id) "Original region selected" else "Show original region",
                                AppCommand.ReviewRegion(source), dispatch)
                        else Text("Original region unavailable", style = MaterialTheme.typography.labelSmall)
                        if (element.kind == SourceElementKind.FIGURE) OriginalFigureBlock(element, media)
                        else SelectionContainer { Text(element.originalText, Modifier.testTag("review-element-${element.id}")) }
                        page.corrections[element.id]?.let { Text("Approved correction: $it") }
                        if (element.kind != SourceElementKind.FIGURE) {
                            SourceCorrectionEditor(source, checksum, element.originalText,
                                corrections.values.filter { it.source == source }, drafts.entries.firstOrNull { it.value.source == source }, dispatch)
                        }
                    }
                }
            }
        }
    }
}
