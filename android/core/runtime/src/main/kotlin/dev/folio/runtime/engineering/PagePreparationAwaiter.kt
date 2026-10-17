package dev.folio.runtime.engineering

import dev.folio.model.command.AppEffect
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Joins the same native jobs as page controls; no picker/voice/approval effects can be awaited here. */
class PagePreparationAwaiter(private val jobs: PagePreparationJobs, private val catalog: SourceCatalog) : AssistantEffectAwaiter {
    override fun supports(effects: List<AppEffect>) = effects.isNotEmpty() && effects.all { it is AppEffect.EngineerPage }
    override suspend fun await(effects: List<AppEffect>): List<PreparedSourceResult> = coroutineScope {
        require(supports(effects))
        effects.map { (it as AppEffect.EngineerPage).request }.distinctBy {
            PageCacheKey(it.documentChecksum, it.documentId, it.pageIndex, it.engineeringVersion)
        }.map { request -> async {
            requireCurrentDocument(request)
            val page = jobs.await(request)
            requireCurrentDocument(request)
            val cached = catalog.page(request.documentId, request.pageIndex) ?: throw PagePreparationException("Prepared page is unavailable. Open the original PDF and try again.")
            require(page.documentId == request.documentId && page.pageIndex == request.pageIndex &&
                page.engineeringVersion == request.engineeringVersion && cached.documentId == request.documentId &&
                cached.pageIndex == request.pageIndex && cached.engineeringVersion >= request.engineeringVersion)
            PreparedSourceResult(request.jobId, SourceAnchor(page.documentId, page.pageIndex), cached.engineeringVersion)
        } }.awaitAll()
    }
    private fun requireCurrentDocument(request: EngineeringRequest) {
        val document = catalog.documents().firstOrNull { it.id == request.documentId }
            ?: throw PagePreparationException("The requested PDF is unavailable. No follow-up action was applied.")
        if (document.checksum != request.documentChecksum || request.pageIndex !in 0 until document.pageCount)
            throw PagePreparationException("PDF identity changed during preparation. No follow-up action was applied.")
    }
}
