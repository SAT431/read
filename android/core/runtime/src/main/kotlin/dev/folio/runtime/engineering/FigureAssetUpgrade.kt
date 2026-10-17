package dev.folio.runtime.engineering

import dev.folio.model.port.*
import dev.folio.model.source.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local-only additive asset upgrade. Stable wording/IDs and saved learner references stay unchanged. */
class FigureAssetUpgrade(private val tools: PdfTools) {
    suspend fun upgrade(request: EngineeringRequest,page: SourcePage): SourcePage {
        require(page.documentId==request.documentId && page.pageIndex==request.pageIndex)
        val width=page.widthPoints ?: return page
        val height=page.heightPoints ?: return page
        return page.copy(elements=page.elements.map { element ->
            currentCoroutineContext().ensureActive()
            if(element.kind!=SourceElementKind.FIGURE || element.mediaBounds!=null || element.bounds==null) element
            else {
                val crop=FigureCropPolicy.context(requireNotNull(element.bounds),width,height)
                element.copy(mediaId=tools.crop(page.documentId,page.pageIndex,crop),mediaBounds=crop)
            }
        })
    }
}
