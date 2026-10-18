package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.read.WorkspaceViewModel
import com.example.read.assistant.*
import dev.folio.assistant.StudyConversationDock
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.study.StudyMode
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.study.*
import dev.folio.study.document.*
import dev.folio.ui.control.SemanticButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentStudyRoute(
    model: WorkspaceViewModel,
    pdf: LibraryPdf,
    state: Workspace,
    assistant: AssistantViewModel,
    voice: VoiceInputViewModel
) {
    val graph = model.graph
    val pdfs by graph.library.entries.collectAsStateWithLifecycle()
    var sourcesOpen by rememberSaveable(pdf.id) { mutableStateOf(false) }
    val outlines by graph.documentOutlines.outlines.collectAsStateWithLifecycle()
    val indexing by graph.documentIndexing.state.collectAsStateWithLifecycle()
    val preparing by graph.studyPreparation.state.collectAsStateWithLifecycle()
    val sourceRevision by graph.pages.revision.collectAsStateWithLifecycle()
    val conversation by assistant.state.collectAsStateWithLifecycle()
    val audio by voice.state.collectAsStateWithLifecycle()
    val gemini by assistant.savedModel.collectAsStateWithLifecycle()
    val saving by assistant.saving.collectAsStateWithLifecycle()
    val plan = state.studyPlans[pdf.id]
    val outline = outlines[pdf.document.id]?.takeIf { it.checksum == pdf.document.checksum }

    LaunchedEffect(pdf.id, plan?.attachedSources) {
        (listOf(pdf) + pdfs.filter { source -> plan?.attachedSources?.any { it.pdfId == source.id } == true })
            .forEach { graph.documentIndexing.ensure(it.document) }
    }

    val projection = remember(plan, pdfs, outlines) {
        runCatching {
            plan?.let { StudySources.resolve(it, pdfs, graph.documentOutlines) }.orEmpty()
        }
    }
    val sources = projection.getOrDefault(emptyList())

    LaunchedEffect(pdf.id, plan?.mode, plan?.preparationVersion, sources) {
        if (plan?.mode == StudyMode.READING && sources.isNotEmpty()) {
            graph.studyPreparation.ensure(pdf.id, plan.preparationVersion)
        }
    }

    val ready = remember(sources, sourceRevision) { StudySources.ready(sources, graph.catalog) }
    val targets = remember(sources, sourceRevision) { StudySources.sentences(sources, graph.catalog) }
    val activeSource = sources.firstOrNull { it.pdf.id == (plan?.activeSourcePdfId ?: pdf.id) }
    val currentReady = remember(activeSource, plan?.pageIndex, sourceRevision) {
        activeSource != null && graph.catalog.page(activeSource.pdf.document.id, plan?.pageIndex ?: 0) != null
    }
    val isReadingMode = plan?.mode == StudyMode.READING && outline != null

    // Full-screen Box layout: 100% top space reclaimed during studying; navigation & AI float cleanly at the bottom!
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (!isReadingMode) {
                StudyHeader(
                    pdf.document.title,
                    false,
                    sources.size.coerceAtLeast(1),
                    activeSource?.pdf?.id ?: pdf.id,
                    pdf.id,
                    { sourcesOpen = true },
                    model::act
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!isReadingMode) {
                    DocumentTopicsScreen(pdf.id, outline, plan, indexing[pdf.document.id], model::act)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        preparing[pdf.id]?.takeIf { it.busy || it.error != null }?.let { work ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(
                                    work.error ?: work.message,
                                    color = if (work.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                if (work.busy) {
                                    LinearProgressIndicator(
                                        progress = { if (work.total > 0) work.completed.toFloat() / work.total else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    SemanticButton("study-pause-preparation", "Pause preparation", AppCommand.CancelStudyWork(pdf.id), model::act)
                                } else {
                                    SemanticButton("study-retry-preparation", "Retry preparation", AppCommand.BeginStudy(pdf.id, plan.version), model::act)
                                }
                            }
                        }
                        if (currentReady) {
                            SelectedTopicReader(
                                plan,
                                outline,
                                graph.catalog,
                                graph.media,
                                state.preferences.readingSizeSp,
                                sourceRevision,
                                sources,
                                model::act,
                                onSources = { sourcesOpen = true }
                            )
                        } else {
                            Text(
                                projection.exceptionOrNull()?.message ?: "Preparing original text and diagram crops…",
                                Modifier.padding(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Minimalist AI Button + Long-Press Radial Menu + Expanding Live Transcription
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            StudyConversationDock(
                pdf.id,
                conversation,
                audio,
                gemini != null && !saving,
                plan?.takeIf { it.mode == StudyMode.READING },
                ready,
                plan?.sentenceId?.let { id -> "Line ${targets.indexOfFirst { it.id == id } + 1} of ${targets.size}" },
                assistant::ask,
                assistant::cancel,
                model::act
            )
        }
    }

    if (sourcesOpen) {
        StudySourcesSheet(pdf, pdfs, outlines, indexing, plan, { sourcesOpen = false }, model::act)
    }
}
