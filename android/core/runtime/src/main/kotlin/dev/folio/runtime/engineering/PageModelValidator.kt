package dev.folio.runtime.engineering

import dev.folio.model.port.EngineeringRequest
import dev.folio.model.source.*

/** Provider-independent checks before a model-produced page enters the source store. */
class PageModelValidator {
    fun validate(request: EngineeringRequest, page: SourcePage) {
        require(page.documentId == request.documentId && page.pageIndex == request.pageIndex) { "Engineering response belongs to another page" }
        require(page.engineeringVersion == request.engineeringVersion) { "Wrong engineering version" }
        require(page.status != EngineeringStatus.REVIEWED) { "AI cannot approve its own transcription" }
        require(page.elements.isNotEmpty() && page.elements.size <= 2000)
        val width = page.widthPoints
        val height = page.heightPoints
        require((width == null) == (height == null)) { "Incomplete page dimensions" }
        if (width != null && height != null) require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        val ids = page.elements.map { it.id }.toSet()
        require(ids.size == page.elements.size && page.elements.all { it.id.isNotBlank() }) { "Duplicate or empty source IDs" }
        require(page.elements.map { it.order }.distinct().size == page.elements.size) { "Ambiguous reading order" }
        page.elements.forEach { element ->
            require(element.order >= 0 && element.originalText.length <= 100_000)
            val bounds = element.bounds
            require(bounds == null || bounds.isValid()) { "Invalid source region" }
            if (bounds != null) require(width != null && height != null && bounds.right <= width && bounds.bottom <= height) {
                "Region exceeds source page bounds"
            }
            require(element.mediaId == null || element.mediaId!!.matches(Regex("[A-Za-z0-9_-]+\\.(png|jpg|jpeg)"))) {
                "Invalid source asset ID"
            }
            element.mediaBounds?.let { crop ->
                require(element.kind==SourceElementKind.FIGURE && crop.isValid() && width!=null && height!=null && crop.right<=width && crop.bottom<=height) { "Invalid original figure crop region" }
                require(bounds!=null && crop.left<=bounds.left && crop.top<=bounds.top && crop.right>=bounds.right && crop.bottom>=bounds.bottom) { "Original figure crop must retain its source region" }
            }
            require(element.parentId == null || element.parentId in ids && element.parentId != element.id)
            require(element.relatedElementIds.all { it in ids }) { "Broken source relationship" }
            val visited = mutableSetOf(element.id)
            var parent = element.parentId
            while (parent != null) {
                require(visited.add(parent)) { "Cyclic source hierarchy" }
                parent = page.elements.first { it.id == parent }.parentId
            }
        }
        require(page.corrections.isEmpty()) { "Provider cannot inject user-approved corrections" }
    }
}
