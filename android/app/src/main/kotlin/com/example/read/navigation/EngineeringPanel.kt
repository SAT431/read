package com.example.read.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.read.WorkspaceViewModel
import com.example.read.engineering.PageEngineeringViewModel
import dev.folio.study.reader.PageEngineeringControls
import dev.folio.ui.media.LocalSourceRevision
import kotlinx.serialization.json.*

@Composable
internal fun EngineeringPanel(workspace: WorkspaceViewModel, engineering: PageEngineeringViewModel, documentId: String, pageIndex: Int) {
    val revision = LocalSourceRevision.current
    val jobs by engineering.jobs.collectAsStateWithLifecycle()
    val job = jobs[PageEngineeringViewModel.key(documentId, pageIndex)]
    val graph = workspace.graph
    val report = graph.catalog.fidelity(documentId, pageIndex)
    val missing = (report?.get("missingEvidenceTokenCount") as? JsonPrimitive)?.intOrNull
    val additional = (report?.get("additionalModelTokenCount") as? JsonPrimitive)?.intOrNull
    val uncertainty = (report?.get("uncertainties") as? JsonArray)?.size
    val available = (report?.get("deterministicTextAvailable") as? JsonPrimitive)?.booleanOrNull == true
    val summary = if (report == null) null else if (!available) "No usable embedded PDF text. Compare the reconstruction with the original image."
        else "Source comparison: ${missing ?: "unknown"} missing / ${additional ?: "unknown"} additional tokens; ${uncertainty ?: "unknown"} uncertainties. Compare the original for accuracy."
    PageEngineeringControls(documentId, pageIndex, graph.catalog.page(documentId, pageIndex) != null,
        job?.busy == true, job?.message, summary, workspace::act)
}
