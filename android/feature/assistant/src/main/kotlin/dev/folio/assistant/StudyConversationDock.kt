package dev.folio.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folio.model.assistant.*
import dev.folio.model.command.AppCommand
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.ui.control.SemanticButton
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class RadialSpot(val label: String, val shortTag: String, val dxDp: Float, val dyDp: Float) {
    SPEAK("Speak Voice", "Mic", -12f, -92f),
    DOUBT("I Have a Doubt (Marker)", "Doubt", -58f, -78f),
    CHAT("Open AI Dialogue", "AI Chat", -88f, -44f),
    GUIDE("Guided Line Study", "Guide", -94f, 4f)
}

/**
 * Renders structured AI Mentor replies with Markdown tables (`| col1 | col2 |`),
 * formulas (`Formula:` / `$$...$$`), and bullet lists inside the AI dialogue and chat sheet.
 */
@Composable
internal fun StructuredAiMessageContent(
    rawText: String,
    textColor: Color = Color(0xFF1F2441),
    modifier: Modifier = Modifier
) {
    val lines = remember(rawText) { rawText.lines() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var idx = 0
        while (idx < lines.size) {
            val line = lines[idx].trim()
            if (line.isEmpty()) {
                idx++
                continue
            }
            // 1. Markdown Table Block: | Col 1 | Col 2 |
            if (line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 3) {
                val tableRows = mutableListOf<List<String>>()
                while (idx < lines.size && lines[idx].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                    val rowLine = lines[idx].trim()
                    val cells = rowLine.substring(1, rowLine.length - 1).split("|").map { it.trim() }
                    val isSeparator = cells.all { it.matches(Regex("^[-:\\s]+$")) }
                    if (!isSeparator && cells.isNotEmpty()) {
                        tableRows.add(cells)
                    }
                    idx++
                }
                if (tableRows.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            tableRows.forEachIndexed { rIdx, rowCells ->
                                val isHeader = rIdx == 0 && tableRows.size > 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isHeader) Color(0xFFEDE9FE) else Color.White)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowCells.forEach { cell ->
                                        Text(
                                            text = cell.replace("**", ""),
                                            color = if (isHeader) Color(0xFF4C1D95) else textColor,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (rIdx < tableRows.lastIndex) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
                continue
            }

            // 2. Formula / Equation Block
            if (line.startsWith("$$") || line.startsWith("Formula:", ignoreCase = true) || line.startsWith("Equation:", ignoreCase = true)) {
                val cleanFormula = line.removeSurrounding("$$").trim()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0FDF4))
                        .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            "FORMULA",
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = cleanFormula.replace("**", ""),
                        color = Color(0xFF14532D),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                idx++
                continue
            }

            // 3. Bullet List Item
            if (line.startsWith("- ") || line.startsWith("• ") || line.startsWith("* ")) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("•", color = Color(0xFF6346C8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = line.drop(2).trim().replace("**", ""),
                        color = textColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                idx++
                continue
            }

            // 4. Standard Paragraph / Subheading
            val isHeading = line.startsWith("#")
            Text(
                text = line.trimStart('#', ' ').replace("**", ""),
                color = if (isHeading) Color(0xFF4C1D95) else textColor,
                fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeading) 14.sp else 13.sp,
                lineHeight = 19.sp
            )
            idx++
        }
    }
}

/**
 * Minimalist Single-Button AI Control with Smooth Zero-Jump Long-Press & Slide Radial Menu,
 * Expanding/Auto-Minimizing Live Voice Transcription Capsule, Animated Working Status Steps,
 * and Practical High-Visibility AI Responding Dialogue Card with Table & Formula Rendering.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyConversationDock(
    pdfId: String,
    state: AssistantState,
    voice: VoiceInputState,
    connected: Boolean,
    plan: DocumentStudyPlan?,
    readerReady: Boolean,
    lineLabel: String?,
    onAsk: (String) -> Boolean,
    onCancel: () -> Unit,
    dispatch: (AppCommand) -> Unit
) {
    var expanded by rememberSaveable(pdfId) { mutableStateOf(false) }
    var dialogueCardOpen by rememberSaveable(pdfId) { mutableStateOf(false) }
    var draft by rememberSaveable(pdfId) { mutableStateOf("") }
    var doubtMarkerActive by rememberSaveable(pdfId) { mutableStateOf(false) }
    var radialMenuOpen by remember { mutableStateOf(false) }
    var hoveredRadial by remember { mutableStateOf<RadialSpot?>(null) }
    var dragAccum by remember { mutableStateOf(Offset.Zero) }

    val replyId = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
    var seenReply by rememberSaveable(pdfId) { mutableStateOf(replyId) }
    val latestReplyText = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.text

    // Automatically pop open practical AI dialogue card when a new assistant reply arrives
    LaunchedEffect(replyId) {
        if (replyId != null && replyId != seenReply) {
            dialogueCardOpen = true
        }
    }

    val statusText = when {
        voice.recording -> "Listening · Speak your doubt or topic…"
        voice.processing -> "Groq Whisper STT transcribing voice…"
        state.status == AssistantStatus.PREPARING -> "Scanning & compiling NCERT visual notes…"
        state.status == AssistantStatus.EXECUTING -> "Applying AI changes to Study Engine canvas…"
        state.busy -> "AI Mentor synthesizing explanation…"
        voice.error != null || state.error != null -> "Request needs attention · tap AI Chat"
        plan?.guided == true -> "${lineLabel.orEmpty()} · Guided mode active"
        else -> null
    }

    val radialProgress by animateFloatAsState(
        targetValue = if (radialMenuOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "radialProgress"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("study-companion")
    ) {
        // 1. Practical High-Visibility AI Responding Dialogue Card (supports tables, formulas & bullets)
        AnimatedVisibility(
            visible = dialogueCardOpen || doubtMarkerActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(
                    2.dp,
                    if (doubtMarkerActive) Color(0xFFF59E0B) else Color(0xFF7C3AED)
                ),
                color = Color.White,
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (doubtMarkerActive) "🖍 Doubt Marker Ready" else "✦ Folio AI Mentor",
                            color = Color(0xFF3D2B80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { expanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("study-chat-toggle")
                            ) {
                                Text("Full Chat", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    dialogueCardOpen = false
                                    doubtMarkerActive = false
                                    seenReply = replyId
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("✕", fontSize = 13.sp)
                            }
                        }
                    }

                    if (doubtMarkerActive) {
                        Text(
                            "Scroll freely and tap any sentence or diagram on screen to mark your doubt, then tap Mic to ask!",
                            color = Color(0xFFB45309),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!latestReplyText.isNullOrBlank()) {
                        StructuredAiMessageContent(rawText = latestReplyText)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { doubtMarkerActive = !doubtMarkerActive },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("study-doubt-marker-toggle")
                        ) {
                            Text(
                                if (doubtMarkerActive) "Exit Marker" else "🖍 I have a doubt",
                                fontSize = 12.sp
                            )
                        }
                        if (plan != null && readerReady) {
                            if (plan.guided && plan.sentenceId != null) {
                                SemanticButton(
                                    "study-finished-line",
                                    "Finished · next",
                                    AppCommand.FinishStudyLine(pdfId, plan.version, requireNotNull(plan.sentenceId)),
                                    dispatch
                                )
                                SemanticButton(
                                    "study-pause-guide",
                                    "Pause",
                                    AppCommand.GuideStudy(pdfId, plan.version, false),
                                    dispatch
                                )
                            } else {
                                SemanticButton(
                                    "study-start-guide",
                                    "Guide me",
                                    AppCommand.GuideStudy(pdfId, plan.version, true),
                                    dispatch
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Expanding Live Voice Transcription & Animated Working Status Capsule
        AnimatedVisibility(
            visible = voice.recording || voice.processing || voice.transcript.isNotBlank() || state.busy || statusText != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (voice.recording) Color(0xFFFDF2F8) else Color(0xFF1E1B4B),
                border = BorderStroke(
                    1.5.dp,
                    if (voice.recording) Color(0xFFEC4899) else Color(0xFF6366F1)
                ),
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    if (state.busy || voice.processing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFA78BFA)
                        )
                    }
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (voice.transcript.isNotBlank()) {
                                "“${voice.transcript}”"
                            } else {
                                statusText ?: "Folio AI Ready"
                            },
                            color = if (voice.recording) Color(0xFF9D174D) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (voice.recording || voice.processing) {
                        SemanticButton("study-cancel-voice", "Cancel", AppCommand.CancelVoice, dispatch)
                    } else if (state.busy) {
                        TextButton(onCancel, enabled = state.status != AssistantStatus.EXECUTING) {
                            Text("Cancel", color = Color(0xFFE0E7FF), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 3. Single Minimalist AI Button with Smooth GPU-Composited Radial Icons (zero layout jumping)
        Box(contentAlignment = Alignment.BottomEnd) {
            if (radialProgress > 0.01f) {
                RadialSpot.entries.forEach { spot ->
                    val isSelected = hoveredRadial == spot
                    val itemScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else radialProgress,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "spotScale"
                    )
                    Surface(
                        onClick = {
                            radialMenuOpen = false
                            when (spot) {
                                RadialSpot.SPEAK -> dispatch(if (voice.recording) AppCommand.FinishVoice else AppCommand.StartVoice)
                                RadialSpot.DOUBT -> {
                                    doubtMarkerActive = !doubtMarkerActive
                                    dialogueCardOpen = true
                                }
                                RadialSpot.CHAT -> expanded = true
                                RadialSpot.GUIDE -> if (plan != null) {
                                    dispatch(AppCommand.GuideStudy(pdfId, plan.version, !plan.guided))
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isSelected) Color(0xFF6346C8) else Color.White,
                        border = BorderStroke(1.5.dp, Color(0xFFD8CCF8)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (spot.dxDp * radialProgress * density).roundToInt(),
                                    (spot.dyDp * radialProgress * density).roundToInt()
                                )
                            }
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                                alpha = radialProgress
                            }
                            .size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                spot.shortTag,
                                color = if (isSelected) Color.White else Color(0xFF3D2B80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Compact Single Master Button (Preserves "study-speak" & quick "study-chat-toggle" access)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!dialogueCardOpen) {
                    Surface(
                        onClick = { dialogueCardOpen = true },
                        shape = RoundedCornerShape(99.dp),
                        color = Color(0xFFF3EFFD),
                        border = BorderStroke(1.dp, Color(0xFFDCCAFA)),
                        modifier = Modifier.testTag("study-chat-toggle")
                    ) {
                        Text(
                            "✦ AI",
                            color = Color(0xFF463A90),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                radialMenuOpen = true
                                dragAccum = Offset.Zero
                                hoveredRadial = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccum += dragAmount
                                val dxDp = dragAccum.x / density
                                val dyDp = dragAccum.y / density
                                hoveredRadial = RadialSpot.entries.minByOrNull { spot ->
                                    hypot(dxDp - spot.dxDp, dyDp - spot.dyDp)
                                }?.takeIf { spot ->
                                    hypot(dxDp - spot.dxDp, dyDp - spot.dyDp) < 48f
                                }
                            },
                            onDragEnd = {
                                val chosen = hoveredRadial
                                radialMenuOpen = false
                                hoveredRadial = null
                                when (chosen) {
                                    RadialSpot.SPEAK -> dispatch(if (voice.recording) AppCommand.FinishVoice else AppCommand.StartVoice)
                                    RadialSpot.DOUBT -> {
                                        doubtMarkerActive = !doubtMarkerActive
                                        dialogueCardOpen = true
                                    }
                                    RadialSpot.CHAT -> expanded = true
                                    RadialSpot.GUIDE -> if (plan != null) {
                                        dispatch(AppCommand.GuideStudy(pdfId, plan.version, !plan.guided))
                                    }
                                    null -> Unit
                                }
                            },
                            onDragCancel = {
                                radialMenuOpen = false
                                hoveredRadial = null
                            }
                        )
                    }
                ) {
                    SemanticButton(
                        "study-speak",
                        if (voice.recording) "Stop" else "🎤 Speak",
                        if (voice.recording) AppCommand.FinishVoice else AppCommand.StartVoice,
                        dispatch,
                        enabled = voice.recording || (connected && !state.busy && !voice.processing)
                    )
                }
            }
        }
    }

    if (expanded) {
        StudyChatSheet(
            state,
            connected,
            draft,
            { draft = it },
            onAsk = {
                val trimmed = draft.trim()
                if (trimmed.contains("i have a doubt", ignoreCase = true)) {
                    doubtMarkerActive = true
                }
                if (onAsk(trimmed)) draft = ""
            },
            onClose = {
                expanded = false
                seenReply = replyId
            },
            onCancel = onCancel,
            error = voice.error ?: state.error
        )
    }
}
