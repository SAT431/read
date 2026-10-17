package com.example.read.engineering

import androidx.lifecycle.ViewModel
import com.example.read.di.AppGraph
import dev.folio.model.port.EngineeringRequest

typealias PageJob = dev.folio.runtime.engineering.PagePreparationStatus
/** Page controls and assistant share app-owned jobs, queue limits and durable cache results. */
class PageEngineeringViewModel(private val graph: AppGraph) : ViewModel() {
    val jobs = graph.pagePreparation.jobs
    fun ensure(request: EngineeringRequest) { graph.pagePreparation.start(request) }
    companion object { fun key(documentId: String, pageIndex: Int) = dev.folio.runtime.engineering.PagePreparationJobs.key(documentId, pageIndex) }
}
