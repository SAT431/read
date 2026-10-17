package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.read.WorkspaceViewModel
import dev.folio.explorer.ExplorerScreen
import dev.folio.library.home.HomeScreen
import dev.folio.library.subject.SubjectScreen
import dev.folio.library.chapter.ChapterLibraryScreen
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.library.settings.AppearanceScreen
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandTrace
import dev.folio.ui.control.SemanticButton
import dev.folio.workspace.WorkspaceScreen
import com.example.read.assistant.AssistantViewModel
import dev.folio.assistant.AssistantScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.read.engineering.PageEngineeringViewModel
import com.example.read.assistant.VoiceInputViewModel
import dev.folio.assistant.VoiceInputPanel

@Composable
internal fun AppContent(model: WorkspaceViewModel, state: Workspace, pdfs: List<LibraryPdf>, traces: List<CommandTrace>, importing: Boolean,
    assistant: AssistantViewModel, engineering: PageEngineeringViewModel, voice: VoiceInputViewModel) {
    val graph = model.graph; val nav = state.navigation
    when (nav.destination) {
        AppDestination.STUDY -> {
            val pdf = pdfs.firstOrNull { it.id == nav.pdfId }
            if (pdf != null) DocumentStudyRoute(model,pdf,state,assistant,voice)
            else Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("This PDF is unavailable. Your saved study records are retained.")
                SemanticButton("study-unavailable-home","Return home",AppCommand.Navigate(AppDestination.HOME),model::act)
            }
        }
        AppDestination.HOME -> HomeScreen(graph.curriculum, pdfs, model::act)
        AppDestination.SUBJECT -> nav.subject?.let { SubjectScreen(it, nav.search, graph.curriculum, pdfs, model::act) }
        AppDestination.CHAPTER -> graph.curriculum.chapters().firstOrNull { it.id == nav.chapterId }?.let {
            ChapterLibraryScreen(it, pdfs, importing, model::act)
        }
        AppDestination.PDF -> pdfs.firstOrNull { it.id == nav.pdfId }?.let {
            PdfStudyRoute(model, engineering, it, nav, state.sourceCorrections, state.correctionDrafts,assistant,voice)
        }
        AppDestination.SEARCH -> {
            val search by graph.search.state.collectAsStateWithLifecycle()
            val ready = if (search.workspaceRevision == state.revision) graph.search.snapshotFor(state) else null
            dev.folio.library.search.StudySearchScreen(state, ready, model::act)
        }
        AppDestination.NOTES -> dev.folio.library.notes.SavedNotesScreen(state, graph.catalog, model::act)
        AppDestination.LEARNING -> dev.folio.library.learning.LearningOverviewScreen(state, graph.catalog, model::act)
        AppDestination.REVISION -> dev.folio.library.revision.RevisionQueueScreen(state, graph.catalog, model::act)
        AppDestination.SETTINGS -> AppearanceScreen(state.preferences, model::act)
        AppDestination.ASSISTANT -> {
            val conversation by assistant.state.collectAsStateWithLifecycle()
            val url by assistant.savedModel.collectAsStateWithLifecycle()
            val saving by assistant.saving.collectAsStateWithLifecycle()
            val error by assistant.connectionError.collectAsStateWithLifecycle()
            val audio by voice.state.collectAsStateWithLifecycle()
            val groq by assistant.groqConfigured.collectAsStateWithLifecycle()
            val review by voice.reviewRequest.collectAsStateWithLifecycle()
            AssistantScreen(conversation, url, saving, error, assistant::save, assistant::disconnect, assistant::ask, assistant::cancel, groq, review) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VoiceInputPanel(url != null && groq && !conversation.busy && !saving, audio.recording, audio.processing, audio.transcript, audio.error,
                    { model.act(AppCommand.StartVoice) }, { model.act(AppCommand.FinishVoice) }, { model.act(AppCommand.CancelVoice) },
                    voice::edit, voice::send, automatic = true)
                }
            }
        }
        AppDestination.EXPLORER -> ExplorerScreen(graph.capabilities, traces, state.preferences.reducedMotion) {
            model.addFixture(it); model.act(AppCommand.Navigate(AppDestination.WORKSPACE))
        }
        AppDestination.WORKSPACE -> {
            if (state.activities.isEmpty()) Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Open a chapter PDF to start your study workspace.")
                SemanticButton("workspace-home", "Explore subjects", AppCommand.Navigate(AppDestination.HOME), model::act)
            } else Column {
                state.context.source?.takeIf { source -> pdfs.any { it.document.id == source.documentId } }?.let {
                    EngineeringPanel(model, engineering, it.documentId, it.pageIndex)
                }
                nav.pdfId?.let { SemanticButton("workspace-original", "Open original PDF", AppCommand.OpenPdf(it), model::act) }
                WorkspaceScreen(state, graph.catalog, graph.media, graph.capabilities,
                    graph.renderers, graph.layoutSolver, model::dispatch, model::interact, { model.undo() })
            }
        }
    }
}
