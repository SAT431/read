package dev.folio.study.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folio.study.reader.ReadingBlock
import dev.folio.study.reader.SentenceText

// Exact color palette from the user's NCERT HTML Notes template
private val HtmlInk = Color(0xFF1F2441)
private val HtmlMuted = Color(0xFF58647F)
private val HtmlBorder = Color(0xFFE4E8F1)
private val HtmlPurple = Color(0xFF815DDF)
private val HtmlPurpleTitle = Color(0xFF6041B1)
private val HtmlTeal = Color(0xFF1CA6A6)
private val HtmlTealTitle = Color(0xFF087C86)
private val HtmlGreenBorder = Color(0xFF24A778)
private val HtmlGreenBg = Color(0xFFEAF9F2)
private val HtmlGreenInk = Color(0xFF185E47)
private val HtmlPink = Color(0xFFDC4295)
private val HtmlOrange = Color(0xFFEE9742)
private val HtmlBlue = Color(0xFF277BC6)

/**
 * Hero Banner matching `<header class="hero">` in the NCERT HTML template.
 */
@Composable
internal fun NcertHtmlHeroBanner(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3D2B80),
                        Color(0xFF6346C8),
                        Color(0xFF8F65E3)
                    )
                )
            )
            .drawBehind {
                // Decorative orbital circles matching .hero::before and .hero::after
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = 120.dp.toPx(),
                    center = Offset(size.width - 20.dp.toPx(), 10.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = 165.dp.toPx(),
                    center = Offset(size.width - 20.dp.toPx(), 10.dp.toPx())
                )
            }
            .padding(horizontal = 28.dp, vertical = 26.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "NCERT BIOLOGY",
                color = Color(0xFFDED5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = title.ifBlank { "What Is a Cell?" },
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 33.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFFECE5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Section Header matching `<div class="section-title"><span class="section-no">01</span><h2>...</h2></div>`
 */
@Composable
internal fun NcertHtmlSectionHeader(
    sectionNo: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFE9DEFE))
                .border(1.dp, Color(0xFFDCCAFA), RoundedCornerShape(13.dp))
        ) {
            Text(
                text = sectionNo,
                color = Color(0xFF5B36BC),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Text(
            text = title,
            color = HtmlInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFDEDCF0),
            thickness = 1.dp
        )
    }
}

/**
 * Automatically highlights important NCERT biological terms, scientist names, years, and definitions in bold.
 */
internal fun styleNcertRichSentence(raw: String, baseColor: Color = HtmlInk, boldColor: Color = HtmlInk): AnnotatedString {
    val highlightPatterns = listOf(
        "independent existence",
        "performing the essential functions of life",
        "cell is the fundamental structural and functional unit of all living organisms",
        "fundamental structural and functional unit",
        "Antonie Von Leeuwenhoek",
        "Robert Brown",
        "electron microscope",
        "Matthias Schleiden",
        "Theodore Schwann",
        "Rudolf Virchow",
        "Schleiden and Schwann",
        "Omnis cellula-e cellula",
        "plasma membrane",
        "cell wall is a unique character of the plant cells",
        "bodies of animals and plants are composed of cells and products of cells",
        "cells and products of cells",
        "pre-existing cells",
        "cells divided and new cells are formed from pre-existing cells",
        "cell theory a final shape",
        "cell theory",
        "did not explain as to how new cells were formed",
        "all plants are composed of different kinds of cells which form the tissues of the plant",
        "cytoplasm is the main arena of cellular activities",
        "eukaryotic",
        "prokaryotic",
        "ribosomes",
        "centrosome",
        "Mycoplasmas",
        "Singer and Nicolson",
        "fluid mosaic model",
        "52 per cent protein and 40 per cent lipids",
        "plasmids",
        "mesosome",
        "70S",
        "80S"
    )
    return buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor)) {
            append(raw)
        }
        for (term in highlightPatterns) {
            var startIndex = raw.indexOf(term, ignoreCase = true)
            while (startIndex >= 0) {
                val endIndex = startIndex + term.length
                addStyle(
                    SpanStyle(fontWeight = FontWeight.ExtraBold, color = boldColor),
                    startIndex,
                    endIndex
                )
                startIndex = raw.indexOf(term, startIndex + term.length, ignoreCase = true)
            }
        }
    }
}

/**
 * Transforms any ReadingBlock into the rich visual NCERT cards from the user's HTML template:
 * - Purple Capability Card (.purple-card) + .capability-list + .important-green
 * - Teal Scientist Discovery Card (.teal-card) + .scientist boxes
 * - Dashed Timeline Cards (.timeline + .timeline-card.schleiden / .schwann / .collaboration / .virchow + .latin)
 * - Modern Theory Card (.theory + .theory-row + .theory-no)
 * While preserving the underlying SentenceText for guided reading & test compatibility!
 */
@Composable
internal fun NcertHtmlNoteCard(
    block: ReadingBlock,
    textSize: Int,
    selectedSentenceId: String?,
    doubtMarkerActive: Boolean = false,
    isDoubtMarked: Boolean = false,
    onSelectSentence: (String) -> Unit
) {
    val raw = block.displayedText.trim()
    val lower = raw.lowercase()

    // Classify the NCERT paragraph into its rich HTML card archetype
    val isWhatIsCellUnicellular = remember(raw) {
        lower.contains("unicellular organisms") ||
            (lower.contains("independent existence") && lower.contains("fundamental structural"))
    }
    val isScientistDiscovery = remember(raw) {
        (lower.contains("leeuwenhoek") || lower.contains("robert brown")) &&
            !lower.contains("schleiden")
    }
    val isCellTheoryTimeline = remember(raw) {
        lower.contains("schleiden") || lower.contains("schwann") || lower.contains("virchow") || lower.contains("1838") || lower.contains("1839") || lower.contains("1855")
    }
    val isModernTheoryOnly = remember(raw) {
        !isCellTheoryTimeline &&
            lower.contains("composed of cells and products of cells") &&
            lower.contains("pre-existing cells")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDoubtMarked) {
                    Modifier
                        .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(23.dp))
                        .background(Color(0xFFFFFDF5), RoundedCornerShape(23.dp))
                } else Modifier
            )
    ) {
        when {
            isWhatIsCellUnicellular -> {
                RenderWhatIsCellPurpleAndTealCards(raw, textSize)
            }
            isScientistDiscovery -> {
                RenderDiscoveryTealCard(raw, textSize)
            }
            isCellTheoryTimeline -> {
                RenderCellTheoryTimelineAndModernCards(raw, textSize)
            }
            isModernTheoryOnly -> {
                RenderModernCellTheoryCard(textSize)
            }
            else -> {
                RenderSmartStructuredNcertCard(raw, textSize)
            }
        }

        // Preserve exact clickable sentence node (`passage-${block.source.id}`) in a compact expandable / linked source strip
        // so both guided-reading sentence highlighting and automated UI tests work seamlessly!
        val hasGuidedSelection = block.sentences.any { it.id == selectedSentenceId }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (hasGuidedSelection || doubtMarkerActive) Color(0xFFF7F3FF) else Color(0xFFF8FAFF),
            border = BorderStroke(
                1.dp,
                if (hasGuidedSelection) HtmlPurple else if (doubtMarkerActive) Color(0xFFF59E0B) else Color(0xFFE8ECF4)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    text = if (doubtMarkerActive) "🖍 Tap any line below to mark your doubt:" else "Verbatim NCERT Interactive Lines (tap any line to focus / ask AI):",
                    color = if (doubtMarkerActive) Color(0xFFB45309) else HtmlMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SelectionContainer {
                    SentenceText(
                        block = block,
                        sizeSp = (textSize - 2).coerceAtLeast(13),
                        selected = selectedSentenceId,
                        annotations = emptyList(),
                        onFocus = onSelectSentence
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderWhatIsCellPurpleAndTealCards(raw: String, textSize: Int) {
    // Card 1: Purple Card - Unicellular Organisms
    HtmlCardContainer(topBorderColor = HtmlPurple) {
        Text(
            text = "Unicellular Organisms",
            color = HtmlPurpleTitle,
            fontWeight = FontWeight.Bold,
            fontSize = (textSize + 2).sp
        )
        Text(
            text = "Unicellular organisms are capable of:",
            color = HtmlInk,
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.65).sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            CapabilityRow("(i)", "Independent existence", textSize)
            CapabilityRow("(ii)", "Performing the essential functions of life", textSize)
        }
        Text(
            text = "Anything less than a complete structure of a cell does not ensure independent living.",
            color = HtmlInk,
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.65).sp
        )
        ImportantGreenBox(
            text = "Hence, cell is the fundamental structural and functional unit of all living organisms.",
            textSize = textSize
        )
    }

    // If the same block also includes Leeuwenhoek / Robert Brown, render the Teal Discovery Card right below it!
    if (raw.contains("Leeuwenhoek", ignoreCase = true) || raw.contains("Robert Brown", ignoreCase = true)) {
        RenderDiscoveryTealCard(raw, textSize)
    }
}

@Composable
private fun RenderDiscoveryTealCard(raw: String, textSize: Int) {
    HtmlCardContainer(topBorderColor = HtmlTeal) {
        Text(
            text = "Discovery of Cell Structures",
            color = HtmlTealTitle,
            fontWeight = FontWeight.Bold,
            fontSize = (textSize + 2).sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScientistSubCard(
                scientist = "Antonie Von Leeuwenhoek",
                contribution = "first saw and described a live cell.",
                textSize = textSize
            )
            ScientistSubCard(
                scientist = "Robert Brown",
                contribution = "later discovered the nucleus.",
                textSize = textSize
            )
        }
        Text(
            text = styleNcertRichSentence(
                "The invention of the microscope and its improvement leading to the electron microscope revealed all the structural details of the cell."
            ),
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.65).sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun RenderCellTheoryTimelineAndModernCards(raw: String, textSize: Int) {
    // Dashed Vertical Timeline container matching `.timeline`
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val x = 10.dp.toPx()
                    drawLine(
                        color = Color(0xFFD8CDEA),
                        start = Offset(x, 18.dp.toPx()),
                        end = Offset(x, size.height - 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                }
                .padding(start = 26.dp)
        ) {
            if (raw.contains("Schleiden", ignoreCase = true)) {
                TimelineCardItem(
                    dotColor = HtmlPink,
                    name = "Matthias Schleiden",
                    year = "1838",
                    yearBg = Color(0xFFFDE9F3),
                    yearColor = Color(0xFFA32B6F),
                    descriptor = "German botanist",
                    bullets = listOf(
                        "Examined a large number of plants.",
                        "Observed that all plants are composed of different kinds of cells which form the tissues of the plant."
                    ),
                    textSize = textSize
                )
            }

            if (raw.contains("Schwann", ignoreCase = true)) {
                TimelineCardItem(
                    dotColor = HtmlOrange,
                    name = "Theodore Schwann",
                    year = "1839",
                    yearBg = Color(0xFFFFF1DC),
                    yearColor = Color(0xFFA75611),
                    descriptor = "German Zoologist",
                    bullets = listOf(
                        "Studied different types of animal cells.",
                        "Reported that cells had a thin outer layer which is today known as the plasma membrane.",
                        "Concluded, based on his studies on plant tissues, that the presence of cell wall is a unique character of the plant cells."
                    ),
                    statement = "On the basis of this, Schwann proposed the hypothesis that the bodies of animals and plants are composed of cells and products of cells.",
                    textSize = textSize
                )
            }

            if (raw.contains("formulated the cell theory", ignoreCase = true) || raw.contains("did not explain", ignoreCase = true)) {
                TimelineCardItem(
                    dotColor = Color(0xFF7D56C9),
                    name = "Schleiden and Schwann",
                    year = null,
                    yearBg = Color.Transparent,
                    yearColor = Color.Transparent,
                    descriptor = null,
                    paragraphs = listOf(
                        "Schleiden and Schwann together formulated the cell theory.",
                        "This theory however, did not explain as to how new cells were formed."
                    ),
                    textSize = textSize
                )
            }

            if (raw.contains("Virchow", ignoreCase = true) || raw.contains("Omnis cellula", ignoreCase = true)) {
                TimelineCardItem(
                    dotColor = HtmlBlue,
                    name = "Rudolf Virchow",
                    year = "1855",
                    yearBg = Color(0xFFE9F2FF),
                    yearColor = Color(0xFF205FAA),
                    descriptor = null,
                    paragraphs = listOf(
                        "First explained that cells divided and new cells are formed from pre-existing cells."
                    ),
                    latinQuote = "Omnis cellula-e cellula",
                    closingParagraph = "He modified the hypothesis of Schleiden and Schwann to give the cell theory a final shape.",
                    textSize = textSize
                )
            }
        }
    }

    if (raw.contains("Cell theory as understood today", ignoreCase = true) || raw.contains("pre-existing cells", ignoreCase = true)) {
        NcertHtmlSectionHeader(sectionNo = "03", title = "Cell Theory as Understood Today")
        RenderModernCellTheoryCard(textSize)
    }
}

@Composable
private fun RenderModernCellTheoryCard(textSize: Int) {
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCCDFD7)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            TheoryRowItem(
                roman = "(i)",
                text = "All living organisms are composed of cells and products of cells.",
                textSize = textSize
            )
            HorizontalDivider(color = Color(0xFFD3E5DD), thickness = 1.dp)
            TheoryRowItem(
                roman = "(ii)",
                text = "All cells arise from pre-existing cells.",
                textSize = textSize
            )
        }
    }
}

@Composable
private fun RenderSmartStructuredNcertCard(raw: String, textSize: Int) {
    val sentences = remember(raw) {
        raw.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    // Check if there are (i), (ii) items inside the paragraph
    val hasRomanNumerals = raw.contains("(i)") && raw.contains("(ii)")
    val conclusionIdx = sentences.indexOfLast {
        it.startsWith("Hence", ignoreCase = true) ||
            it.startsWith("Therefore", ignoreCase = true) ||
            it.startsWith("Thus", ignoreCase = true) ||
            it.contains("main arena of cellular activities", ignoreCase = true) ||
            it.contains("fluid mosaic model", ignoreCase = true)
    }

    val bodySentences = if (conclusionIdx > 0) sentences.take(conclusionIdx) else sentences
    val conclusionSentence = if (conclusionIdx > 0) sentences.drop(conclusionIdx).joinToString(" ") else null

    HtmlCardContainer(topBorderColor = if (hasRomanNumerals) HtmlPurple else HtmlTeal) {
        if (bodySentences.size <= 2) {
            bodySentences.forEach { sentence ->
                Text(
                    text = styleNcertRichSentence(sentence),
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.68).sp
                )
            }
        } else {
            // Render first sentence as lead-in, middle sentences as structured capability/key-point boxes
            Text(
                text = styleNcertRichSentence(bodySentences.first()),
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.68).sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bodySentences.drop(1).forEachIndexed { idx, point ->
                    CapabilityRow(
                        roman = "(${idx + 1})",
                        text = point,
                        textSize = textSize
                    )
                }
            }
        }

        if (conclusionSentence != null) {
            ImportantGreenBox(text = conclusionSentence, textSize = textSize)
        }
    }
}

@Composable
private fun HtmlCardContainer(
    topBorderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = Color.White,
        border = BorderStroke(1.dp, HtmlBorder),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(topBorderColor)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CapabilityRow(roman: String, text: String, textSize: Int) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF7F3FF))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            text = roman,
            color = Color(0xFF7E53C6),
            fontWeight = FontWeight.Bold,
            fontSize = (textSize - 2).sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = styleNcertRichSentence(text),
            color = HtmlInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.55).sp
        )
    }
}

@Composable
private fun ImportantGreenBox(text: String, textSize: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HtmlGreenBg)
            .border(1.dp, Color(0xFFBCEAD4), RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(HtmlGreenBorder)
        )
        Text(
            text = styleNcertRichSentence(text, baseColor = HtmlGreenInk, boldColor = Color(0xFF116948)),
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.6).sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)
        )
    }
}

@Composable
private fun ScientistSubCard(scientist: String, contribution: String, textSize: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0FAFB))
            .border(1.dp, Color(0xFFD2EEF0), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF006F7B), fontWeight = FontWeight.ExtraBold)) {
                    append("$scientist ")
                }
                withStyle(SpanStyle(color = HtmlInk)) {
                    append(contribution)
                }
            },
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.55).sp
        )
    }
}

@Composable
private fun TimelineCardItem(
    dotColor: Color,
    name: String,
    year: String?,
    yearBg: Color,
    yearColor: Color,
    descriptor: String?,
    bullets: List<String> = emptyList(),
    paragraphs: List<String> = emptyList(),
    statement: String? = null,
    latinQuote: String? = null,
    closingParagraph: String? = null,
    textSize: Int
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Timeline dot on the left
        Box(
            modifier = Modifier
                .offset(x = (-22).dp, y = 26.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(3.dp, Color(0xFFF7F8FE), CircleShape)
        )

        HtmlCardContainer(topBorderColor = dotColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = name,
                    color = HtmlInk,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = (textSize + 3).sp
                )
                if (year != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(yearBg)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = year,
                            color = yearColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (descriptor != null) {
                Text(
                    text = descriptor,
                    color = HtmlMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bullets.forEach { bullet ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "•",
                                color = dotColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (textSize + 2).sp
                            )
                            Text(
                                text = styleNcertRichSentence(bullet),
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.6).sp
                            )
                        }
                    }
                }
            }

            paragraphs.forEach { p ->
                Text(
                    text = styleNcertRichSentence(p),
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.6).sp
                )
            }

            if (latinQuote != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEAF2FF))
                        .border(1.dp, Color(0xFFCBDFFC), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = latinQuote,
                        color = Color(0xFF275BAE),
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = (textSize + 2).sp
                    )
                }
            }

            if (closingParagraph != null) {
                Text(
                    text = styleNcertRichSentence(closingParagraph),
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.6).sp
                )
            }

            if (statement != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFFFF7EA))
                        .border(1.dp, Color(0xFFF2DFBB), RoundedCornerShape(13.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = styleNcertRichSentence(
                            statement,
                            baseColor = Color(0xFF754E17),
                            boldColor = Color(0xFF5C3B0E)
                        ),
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.6).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TheoryRowItem(roman: String, text: String, textSize: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE3F8ED))
        ) {
            Text(
                text = roman,
                color = Color(0xFF087E58),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Text(
            text = styleNcertRichSentence(text, baseColor = HtmlInk, boldColor = Color(0xFF147D5B)),
            fontWeight = FontWeight.SemiBold,
            fontSize = (textSize + 1).sp,
            lineHeight = (textSize * 1.6).sp
        )
    }
}
