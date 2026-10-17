package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.read.WorkspaceViewModel
import com.example.read.LibraryViewModel
import dev.folio.model.command.AppCommand
import dev.folio.model.workspace.AppDestination
import androidx.activity.compose.BackHandler
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.explorer.ExplorerScreen
import dev.folio.workspace.WorkspaceScreen
import com.example.read.assistant.AssistantViewModel
import dev.folio.assistant.AssistantStatusBar
import dev.folio.assistant.VoiceStatusBar
import com.example.read.engineering.PageEngineeringViewModel
import dev.folio.ui.media.LocalSourceRevision
import com.example.read.assistant.VoiceInputViewModel

@Composable
fun FolioRoot(viewModel: WorkspaceViewModel, libraryModel: LibraryViewModel, assistant: AssistantViewModel, engineering: PageEngineeringViewModel, voice: VoiceInputViewModel) {
    val graph = viewModel.graph
    val cacheRepair by graph.cacheRepair.state.collectAsStateWithLifecycle()
    LaunchedEffect(cacheRepair.phase) {
        if (cacheRepair.phase == dev.folio.runtime.source.SourceCacheRepairPhase.DONE) viewModel.clearPageRecoveryWarning()
    }
    val state by graph.session.state.collectAsStateWithLifecycle()
    val traces by graph.session.traces.collectAsStateWithLifecycle()
    val storageError by graph.session.storageError.collectAsStateWithLifecycle()
    val recoveredSources by graph.session.recoveredUnavailableSources.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val pdfs by graph.library.entries.collectAsStateWithLifecycle()
    val importing by libraryModel.importing.collectAsStateWithLifecycle()
    val importError by libraryModel.error.collectAsStateWithLifecycle()
    val assistantState by assistant.state.collectAsStateWithLifecycle()
    val geminiModel by assistant.savedModel.collectAsStateWithLifecycle()
    val keysSaving by assistant.saving.collectAsStateWithLifecycle()
    val groqConfigured by assistant.groqConfigured.collectAsStateWithLifecycle()
    val sourceRevision by graph.pages.revision.collectAsStateWithLifecycle()
    PdfImportLauncher(viewModel, libraryModel, assistant, engineering, voice)
    val voiceState by voice.state.collectAsStateWithLifecycle()
    BackHandler(state.navigation.destination != AppDestination.HOME) { viewModel.act(AppCommand.Back) }
    FolioTheme(state.preferences.darkTheme, state.preferences.reducedMotion) {
        dev.folio.library.settings.SourceCacheRepairDialog(cacheRepair, viewModel::act)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth >= 840.dp
                val fullScreen = state.navigation.destination in setOf(AppDestination.STUDY, AppDestination.PDF)
                Row(Modifier.fillMaxSize()) {
                if (wide && !fullScreen) NavigationSidebar(state.navigation.destination, viewModel::act)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!fullScreen || state.navigation.sourceReviewVisible) AppTopBar(state.navigation.destination, viewModel::act, wide || fullScreen)
                if (error != null || storageError != null || importError != null) Text(storageError ?: error ?: importError.orEmpty(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                RecoveredSourceNotice(state, recoveredSources, graph.catalog, sourceRevision)
                if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
                else Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalSourceRevision provides sourceRevision,
                    dev.folio.ui.interaction.LocalInteractionGate provides graph.interactions) {
                    AppContent(viewModel, state, pdfs, traces, importing, assistant, engineering, voice)
                } }
                if (!fullScreen) {
                AssistantStatusBar(assistantState, assistant::cancel, assistant::retry)
                VoiceStatusBar(voiceState, geminiModel != null && groqConfigured && !keysSaving && !assistantState.busy,
                    { viewModel.act(AppCommand.StartVoice) }, { viewModel.act(AppCommand.FinishVoice) },
                    { viewModel.act(AppCommand.CancelVoice) }, { viewModel.act(AppCommand.ReviewVoice) })
                }
                if (!wide && !fullScreen) CompactNavigation(state.navigation.destination, viewModel::act)
                }
                }
            }
        }
    }
}
