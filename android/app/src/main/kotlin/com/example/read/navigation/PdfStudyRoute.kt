package com.example.read.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.example.read.assistant.*
import dev.folio.assistant.StudyConversationDock
import androidx.compose.ui.Modifier
import com.example.read.WorkspaceViewModel
import com.example.read.engineering.PageEngineeringViewModel
import dev.folio.library.pdf.OriginalPdfPage
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.AppNavigation
import dev.folio.study.review.SourceReviewScreen
import dev.folio.ui.media.LocalSourceRevision

@Composable
internal fun PdfStudyRoute(model: WorkspaceViewModel, engineering: PageEngineeringViewModel, pdf: LibraryPdf, nav: AppNavigation,
    corrections: Map<String, dev.folio.model.source.SourceCorrection>, drafts: Map<String, dev.folio.model.source.SourceCorrectionDraft>,assistant:AssistantViewModel,voice:VoiceInputViewModel) {
    val revision = LocalSourceRevision.current
    val graph = model.graph
    val page = graph.catalog.page(pdf.document.id, nav.pdfPageIndex)
    val conversation by assistant.state.collectAsStateWithLifecycle()
    val audio by voice.state.collectAsStateWithLifecycle()
    val gemini by assistant.savedModel.collectAsStateWithLifecycle()
    val saving by assistant.saving.collectAsStateWithLifecycle()
    val jobs by engineering.jobs.collectAsStateWithLifecycle()
    val job=jobs[PageEngineeringViewModel.key(pdf.document.id,nav.pdfPageIndex)]
    Column(Modifier.fillMaxSize().imePadding()) {
        if(nav.sourceReviewVisible && page==null) EngineeringPanel(model,engineering,pdf.document.id,nav.pdfPageIndex)
        else if(job!=null) Text(job.message,Modifier.padding(horizontal=20.dp,vertical=4.dp),style=androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Box(Modifier.weight(1f)) {
            if (nav.sourceReviewVisible) SourceReviewScreen(pdf, nav, page,
                graph.catalog.fidelity(pdf.document.id, nav.pdfPageIndex), graph.media, model::act, corrections, drafts) { region ->
                OriginalPdfPage(pdf, nav, graph.pdfTools, graph.media, model::act, region)
            } else OriginalPdfScreen(pdf, nav, graph.pdfTools, graph.media, model::act)
        }
        StudyConversationDock(pdf.id,conversation,audio,gemini!=null && !saving,null,false,null,assistant::ask,assistant::cancel,model::act)
    }
}
