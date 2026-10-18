package dev.folio.study.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folio.designsystem.theme.LocalReducedMotion
import dev.folio.model.command.AppCommand
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.runtime.study.*
import dev.folio.ui.control.SemanticButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedTopicReader(
    plan: DocumentStudyPlan,
    outline: DocumentOutline,
    catalog: SourceCatalog,
    media: MediaRepository,
    textSize: Int,
    sourceRevision: Long,
    sources: List<StudySource> = emptyList(),
    dispatch: (AppCommand) -> Unit,
    onSources: () -> Unit = {}
) {
    val active = sources.firstOrNull { it.pdf.id == (plan.activeSourcePdfId ?: plan.pdfId) }
    val references = StudySources.pages(sources)
    val surface = if (sources.isNotEmpty()) StudyComposition.surface(plan, sources) else null
    val sections = surface?.sections?.map { ref ->
        StudyReadingSection(
            ref.source.pdf.id,
            ref.source.pdf.document.id,
            ref.pageIndex,
            ref.source.pdf.document.title,
            DocumentStudyRules.topics(ref.source.plan, ref.source.outline)
        )
    } ?: listOf(
        StudyReadingSection(
            plan.pdfId,
            outline.documentId,
            plan.pageIndex,
            catalog.documents().firstOrNull { it.id == outline.documentId }?.title.orEmpty(),
            runCatching { DocumentStudyRules.topics(plan, outline) }.getOrDefault(emptyList())
        )
    )
    val position = if (sources.isEmpty()) plan.pages.indexOf(plan.pageIndex) else references.indexOfFirst {
        it.source.pdf.id == (plan.activeSourcePdfId ?: plan.pdfId) && it.pageIndex == plan.pageIndex
    }
    val total = if (sources.isEmpty()) plan.pages.size else references.size

    fun pageCommand(index: Int): AppCommand =
        if (sources.isEmpty()) AppCommand.StudyPage(plan.pdfId, plan.version, plan.pages[index])
        else AppCommand.StudyPage(plan.pdfId, plan.version, references[index].pageIndex, references[index].source.pdf.id)

    val rows = remember(sections, sourceRevision) { readingRows(sections, catalog) }
    val scroll = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    var lastTarget by remember { mutableStateOf<String?>(null) }
    var toolsMenuOpen by remember { mutableStateOf(false) }
    val activeKey = "${active?.pdf?.document?.id ?: outline.documentId}:${plan.pageIndex}"
    val targetKey = plan.sentenceId?.let { "sentence:$it" } ?: "page:$activeKey"

    LaunchedEffect(targetKey, rows, reducedMotion) {
        if (lastTarget != targetKey) {
            val target = if (plan.sentenceId != null) {
                rows.indexOfFirst { it is StudyReadingRow.Content && it.block.sentences.any { line -> line.id == plan.sentenceId } }
            } else {
                rows.indexOfFirst { it is StudyReadingRow.Source && it.section.key == activeKey }
            }
            if (target >= 0) {
                if (reducedMotion) scroll.scrollToItem(target) else scroll.animateScrollToItem(target)
                lastTarget = targetKey
            }
        }
    }

    // Full-screen Box: study notes start immediately at the very top with zero wasted header bars;
    // minimal Previous/Next Page & Topics dock floats unobtrusively at the bottom-left!
    Box(
        Modifier
            .fillMaxSize()
            .testTag("selected-topic-reader")
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .testTag("study-reading-list"),
            state = scroll,
            contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is StudyReadingRow.Source -> StudySourceHeader(row, plan, row.section.key != sections.firstOrNull()?.key, dispatch)
                    is StudyReadingRow.Content -> Box(
                        Modifier
                            .fillMaxWidth()
                            .testTag("study-block-${row.section.documentId}-${row.section.pageIndex}-${row.block.source.id}")
                    ) {
                        StudyReadingContent(row, plan, media, textSize, dispatch)
                    }
                    is StudyReadingRow.Notice -> Text(row.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Minimal Bottom-Left Floating Navigation & Page Controls Dock
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xE8191D34),
            border = BorderStroke(1.dp, Color(0x28FFFFFF)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
                .testTag("study-bottom-page-dock")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                TextButton(
                    onClick = { dispatch(AppCommand.Back) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("study-exit")
                ) {
                    Text("←", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { dispatch(AppCommand.ShowStudyTopics(plan.pdfId)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("study-topic-map")
                ) {
                    Text("Topics", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (total > 1) {
                    if (position > 0) {
                        SemanticButton("study-previous-page", "‹ Prev", pageCommand(position - 1), dispatch)
                    }
                    Text(
                        "${position + 1}/$total",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (position < total - 1) {
                        SemanticButton("study-next-page", "Next ›", pageCommand(position + 1), dispatch)
                    }
                }
                Box {
                    TextButton(
                        onClick = { toolsMenuOpen = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .testTag("study-tools")
                            .semantics { contentDescription = "Study tools" }
                    ) {
                        Text("⋯", color = Color.White, fontSize = 12.sp)
                    }
                    DropdownMenu(toolsMenuOpen, { toolsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("PDF sources (${sources.size.coerceAtLeast(1)})") },
                            onClick = { toolsMenuOpen = false; onSources() },
                            modifier = Modifier.testTag("study-sources")
                        )
                        DropdownMenuItem(
                            text = { Text("Open original PDF") },
                            onClick = {
                                toolsMenuOpen = false
                                dispatch(AppCommand.OpenPdf(active?.pdf?.id ?: plan.pdfId))
                            },
                            modifier = Modifier.testTag("study-original")
                        )
                    }
                }
            }
        }
    }
}
