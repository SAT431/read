package dev.folio.runtime.action

import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.*
import dev.folio.runtime.engineering.SourceRegions

class SourceReviewNavigation(private val catalog: SourceCatalog, private val navigation: LibraryNavigationReducer) {
    fun apply(state: Workspace, command: AppCommand.ReviewRegion, pdfs: List<LibraryPdf>): Workspace {
        val source = command.source
        if (source == null) {
            require(state.navigation.sourceReviewVisible && pdfs.any { it.id == state.navigation.pdfId }) { "No active source comparison" }
            return state.copy(navigation = state.navigation.copy(destination = AppDestination.PDF, reviewElementId = null))
        }
        require(catalog.contains(source) && SourceRegions.resolve(catalog.page(source.documentId, source.pageIndex), source) != null) {
            "Original source region unavailable"
        }
        val opened = navigation.reduce(state, AppCommand.ReviewPage(source.documentId, source.pageIndex), pdfs)!!
        return opened.copy(navigation = opened.navigation.copy(reviewElementId = source.elementId))
    }
}
