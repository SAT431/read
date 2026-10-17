package dev.folio.runtime.assistant

import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.*
import dev.folio.runtime.engineering.SourceRegions

object AssistantSourceSelection {
    fun viewingPdf(state: Workspace) = state.navigation.destination == AppDestination.PDF ||
        (state.navigation.destination == AppDestination.ASSISTANT && state.navigation.assistantOrigin == AppDestination.PDF)

    fun resolve(state: Workspace, pdfs: List<LibraryPdf>, catalog: SourceCatalog): SourceAnchor? {
        if (!viewingPdf(state)) return state.context.source
        val nav = state.navigation
        val document = pdfs.firstOrNull { it.id == nav.pdfId }?.document ?: return null
        val page = SourceAnchor(document.id, nav.pdfPageIndex)
        val element = nav.reviewElementId?.takeIf { nav.sourceReviewVisible } ?: return page
        val selected = page.copy(elementId = element)
        return selected.takeIf { SourceRegions.resolve(catalog.page(document.id, page.pageIndex), it) != null } ?: page
    }
}
