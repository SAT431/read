package dev.folio.study.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.SourceElementKind
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.study.reader.*
import dev.folio.ui.control.SemanticButton

/**
 * Renders extracted NCERT study blocks in the exact HTML visual note layout:
 * - Section Headings with NCERT badge
 * - Purple Top-Accent Cards with Roman Numeral (i)/(ii) capability rows, structured bullet points,
 *   structured comparison/parameter tables (e.g., Centrosome & Centrioles, Organelle parameters),
 *   & Green Left-Bordered Important Callouts
 * - Teal Top-Accent Cards with #F0FAFB Scientist/Structure discovery sub-boxes
 * - Vertical Timeline Track Cards (Schleiden 1838, Schwann 1839, Virchow 1855) with Year Pills, Descriptors, Bullets, Statement Boxes & Latin Pills
 * while preserving 100% of verbatim NCERT wording and interactive SentenceText testTags.
 */
@Composable
internal fun StudyReadingContent(
    row: StudyReadingRow.Content,
    plan: DocumentStudyPlan,
    media: MediaRepository,
    textSize: Int,
    dispatch: (AppCommand) -> Unit
) {
    val block = row.block
    val text = block.displayedText.trim()

    when (block.source.kind) {
        SourceElementKind.HEADING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE9DEFE),
                    border = BorderStroke(1.dp, Color(0xFFDCCAFA))
                ) {
                    Text(
                        "NCERT",
                        color = Color(0xFF5B36BC),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1F2441),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        SourceElementKind.FIGURE -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E8F1)),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color(0xFF2C63CC))
                    )
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OriginalFigureBlock(block.source, media)
                        SemanticButton(
                            "study-enlarge-${row.section.documentId}-${block.source.id}",
                            "Enlarge original",
                            AppCommand.HarnessInspectPage(row.section.pdfId, row.section.pageIndex),
                            dispatch
                        )
                    }
                }
            }
        }
        else -> {
            val isTimelineBlock = text.contains("1838") || text.contains("1839") ||
                text.contains("1855") || text.contains("Schleiden", ignoreCase = true) ||
                text.contains("Schwann", ignoreCase = true) || text.contains("Virchow", ignoreCase = true)

            val isScientistDiscoveryBlock = !isTimelineBlock && (
                text.contains("Leeuwenhoek", ignoreCase = true) ||
                    text.contains("Robert Brown", ignoreCase = true) ||
                    text.contains("Singer", ignoreCase = true) ||
                    text.contains("Camillo Golgi", ignoreCase = true) ||
                    text.contains("George Palade", ignoreCase = true) ||
                    text.contains("Flemming", ignoreCase = true)
                )

            val isCentrosomeBlock = text.contains("centrosome", ignoreCase = true) ||
                text.contains("centriole", ignoreCase = true) ||
                text.contains("cartwheel", ignoreCase = true) ||
                text.contains("pericentriolar", ignoreCase = true)

            // Parse Roman numeral clauses (i) ... (ii) ...
            val romanItems = remember(text) {
                val regex = Regex("""\(([ivx]+)\)\s*([^(]+?)(?=(?:\([ivx]+\)|\.|$))""", RegexOption.IGNORE_CASE)
                regex.findAll(text).map { match ->
                    "(${match.groupValues[1].lowercase()})" to match.groupValues[2].trim().trimEnd('.', ';', ',')
                }.toList()
            }

            // Parse structured sentences for bullet points when paragraph has >= 2 sentences
            val sentenceBullets = remember(text) {
                text.split(Regex("""(?<=[.!?])\s+"""))
                    .map { it.trim() }
                    .filter { it.length > 12 }
            }

            // Parse concluding green callout sentence
            val concludingSentence = remember(text) {
                sentenceBullets.firstOrNull {
                    it.startsWith("Hence,", ignoreCase = true) ||
                        it.startsWith("Therefore,", ignoreCase = true) ||
                        it.contains("fundamental structural and functional unit", ignoreCase = true) ||
                        it.contains("main arena of cellular activities", ignoreCase = true) ||
                        it.contains("basal body of cilia or flagella", ignoreCase = true) ||
                        it.contains("spindle apparatus during cell division", ignoreCase = true)
                }
            }

            if (isTimelineBlock) {
                Row(Modifier.fillMaxWidth().padding(start = 6.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 12.dp, top = 18.dp)
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC4295))
                                .border(3.dp, Color(0xFFF7F8FE), CircleShape)
                        )
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(140.dp)
                                .background(Color(0xFFD8CDEA))
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color(0xFFE4E8F1)),
                        shadowElevation = 2.dp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFDC4295), Color(0xFFEE9742), Color(0xFF277BC6))
                                        )
                                    )
                            )
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (text.contains("1838") || text.contains("Schleiden", ignoreCase = true)) {
                                        TimelineBadge("Matthias Schleiden", "1838", Color(0xFFFDE9F3), Color(0xFFA32B6F))
                                    }
                                    if (text.contains("1839") || text.contains("Schwann", ignoreCase = true)) {
                                        TimelineBadge("Theodore Schwann", "1839", Color(0xFFFFF1DC), Color(0xFFA75611))
                                    }
                                    if (text.contains("1855") || text.contains("Virchow", ignoreCase = true)) {
                                        TimelineBadge("Rudolf Virchow", "1855", Color(0xFFE9F2FF), Color(0xFF205FAA))
                                    }
                                }

                                SelectionContainer {
                                    SentenceText(block, textSize, plan.sentenceId, emptyList()) { sentence ->
                                        dispatch(AppCommand.StudySentence(plan.pdfId, plan.version, sentence))
                                    }
                                }

                                if (text.contains("Omnis cellula", ignoreCase = true)) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEAF2FF),
                                        border = BorderStroke(1.dp, Color(0xFFCBDFFC))
                                    ) {
                                        Text(
                                            "Omnis cellula-e cellula (New cells arise from pre-existing cells)",
                                            color = Color(0xFF275BAE),
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                if (romanItems.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFF8FBF9),
                                        border = BorderStroke(1.dp, Color(0xFFCCDFD7)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            romanItems.forEach { (roman, itemText) ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFE3F8ED)
                                                    ) {
                                                        Text(
                                                            roman,
                                                            color = Color(0xFF087E58),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    Text(
                                                        itemText,
                                                        color = Color(0xFF1F2441),
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (isScientistDiscoveryBlock) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE4E8F1)),
                    shadowElevation = 2.dp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1CA6A6), Color(0xFF38B2AC))
                                    )
                                )
                        )
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Discovery & Structural Details",
                                color = Color(0xFF087C86),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF0FAFB),
                                border = BorderStroke(1.dp, Color(0xFFD2EEF0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(Modifier.padding(14.dp)) {
                                    SelectionContainer {
                                        SentenceText(block, textSize, plan.sentenceId, emptyList()) { sentence ->
                                            dispatch(AppCommand.StudySentence(plan.pdfId, plan.version, sentence))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE4E8F1)),
                    shadowElevation = 2.dp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF815DDF), Color(0xFF9F7AEA))
                                    )
                                )
                        )
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Structured subheading + bullet breakdown so dense paragraphs are cleanly organized
                            if (sentenceBullets.size >= 2) {
                                Text(
                                    if (isCentrosomeBlock) "Centrosome & Centriole Organization" else "Structured NCERT Key Points",
                                    color = Color(0xFF6041B1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sentenceBullets.forEachIndexed { idx, bullet ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF7F3FF),
                                            border = BorderStroke(1.dp, Color(0xFFE5DDFB)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    "(${idx + 1})",
                                                    color = Color(0xFF7E53C6),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    styleNcertRichSentence(bullet),
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Structured NCERT Table for Centrosome & Centrioles when centrosome text is present
                            if (isCentrosomeBlock) {
                                CentrosomeSummaryTable()
                            }

                            // Interactive verbatim NCERT passage (preserves SentenceText & testTag)
                            SelectionContainer {
                                SentenceText(block, textSize, plan.sentenceId, emptyList()) { sentence ->
                                    dispatch(AppCommand.StudySentence(plan.pdfId, plan.version, sentence))
                                }
                            }

                            // Capability list pills (i), (ii) matching HTML .capability-list
                            if (romanItems.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    romanItems.forEach { (roman, itemText) ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF7F3FF),
                                            border = BorderStroke(1.dp, Color(0xFFE5DDFB)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFE2D7FD)
                                                ) {
                                                    Text(
                                                        roman,
                                                        color = Color(0xFF7E53C6),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    itemText,
                                                    color = Color(0xFF1F2441),
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Green Important Callout Box matching HTML .important-green
                            if (concludingSentence != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFEAF9F2),
                                    border = BorderStroke(1.dp, Color(0xFFBCEAD4)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Box(
                                            Modifier
                                                .width(5.dp)
                                                .heightIn(min = 44.dp)
                                                .background(Color(0xFF24A778))
                                        )
                                        Text(
                                            concludingSentence,
                                            color = Color(0xFF116948),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CentrosomeSummaryTable() {
    val rows = listOf(
        "Structural Component" to "NCERT Specification",
        "Centriole Pair" to "Two cylindrical structures lying perpendicular to each other, surrounded by amorphous pericentriolar materials",
        "Organisation" to "Cartwheel-like (9+0); 9 evenly spaced peripheral fibrils of tubulin protein (triplets); no central microtubule",
        "Central Hub & Spokes" to "Proteinaceous hub connected with tubules of peripheral triplets by radial spokes",
        "Key Functions" to "Forms basal body of cilia/flagella and spindle fibres that give rise to spindle apparatus in animal cell division"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFD2EEF0)),
        color = Color(0xFFF0FAFB),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { idx, (col1, col2) ->
                val isHeader = idx == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeader) Color(0xFFE0F2FE) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        col1,
                        color = if (isHeader) Color(0xFF087C86) else Color(0xFF006F7B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.36f)
                    )
                    Text(
                        col2,
                        color = Color(0xFF1F2441),
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(0.64f)
                    )
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(color = Color(0xFFD2EEF0), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TimelineBadge(name: String, year: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(name, color = Color(0xFF1F2441), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(year, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}
