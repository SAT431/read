package com.example.read.navigation

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.read.LibraryViewModel
import com.example.read.WorkspaceViewModel
import dev.folio.model.command.AppEffect
import com.example.read.assistant.AssistantViewModel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import com.example.read.engineering.PageEngineeringViewModel
import com.example.read.assistant.VoiceInputViewModel

@Composable
internal fun PdfImportLauncher(workspace: WorkspaceViewModel, library: LibraryViewModel, assistant: AssistantViewModel, engineering: PageEngineeringViewModel, voice: VoiceInputViewModel) {
    val context = LocalContext.current
    val startVoice = rememberStartVoice(voice)
    var pendingChapter by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val chapter = pendingChapter; pendingChapter = null
        if (uri != null && chapter != null) {
            val title = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } }.getOrNull() ?: "Chapter PDF"
            library.importPdf(uri, chapter, title)
        }
    }
    LaunchedEffect(workspace, assistant) {
        merge(workspace.effects, assistant.effects).collect { effect -> when (effect) {
            is AppEffect.AnalyzeStudy -> workspace.graph.library.entries.value.firstOrNull { it.id == effect.pdfId }?.let {
                workspace.graph.documentIndexing.ensure(it.document, retry = true, requestId=effect.requestId.ifBlank{null})
            }.let { Unit }
            is AppEffect.PrepareStudy -> workspace.graph.studyPreparation.ensure(effect.pdfId, effect.version)
            is AppEffect.CancelStudy -> {
                workspace.graph.library.entries.value.firstOrNull { it.id == effect.pdfId }?.let { workspace.graph.documentIndexing.cancel(it.document.id) }
                workspace.graph.studyPreparation.cancel(effect.pdfId)
            }
            AppEffect.CheckSourceCache -> workspace.graph.persistenceScope.launch { workspace.graph.cacheRepair.request() }.let { Unit }
            is AppEffect.ConfirmSourceCacheRepair -> workspace.graph.persistenceScope.launch { workspace.graph.cacheRepair.confirm(effect.token) }.let { Unit }
            AppEffect.DismissSourceCacheRepair -> workspace.graph.persistenceScope.launch { workspace.graph.cacheRepair.dismiss() }.let { Unit }
            AppEffect.StartVoice -> startVoice()
            AppEffect.FinishVoice -> voice.finish()
            AppEffect.CancelVoice -> voice.cancel()
            AppEffect.ReviewVoice -> voice.review()
            is AppEffect.EngineerPage -> engineering.ensure(effect.request)
            is AppEffect.RequestPdfImport -> if (pendingChapter == null) {
                pendingChapter = effect.chapterId; picker.launch(arrayOf("application/pdf"))
            }
        } }
    }
}
