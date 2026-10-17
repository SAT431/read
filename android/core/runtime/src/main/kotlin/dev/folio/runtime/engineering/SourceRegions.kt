package dev.folio.runtime.engineering

import dev.folio.model.source.*
import kotlin.math.abs
import kotlin.math.min

/** No guessed regions: only validated, bounded coordinates from the matching page. */
object SourceRegions {
    fun resolve(page: SourcePage?, source: SourceAnchor): SourceRegion? {
        if (page == null || page.documentId != source.documentId || page.pageIndex != source.pageIndex) return null
        val width = page.widthPoints ?: return null
        val height = page.heightPoints ?: return null
        val bounds = page.elements.firstOrNull { it.id == source.elementId }?.bounds ?: return null
        if (!width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 ||
            !bounds.isValid() || bounds.right > width || bounds.bottom > height) return null
        return SourceRegion(source, bounds, width, height)
    }

    /** Centered ContentScale.Fit before the viewport's shared zoom/pan transform. */
    fun project(region: SourceRegion, imageWidth: Float, imageHeight: Float,
        viewportWidth: Float, viewportHeight: Float): SourceBounds? {
        val dimensions = listOf(imageWidth, imageHeight, viewportWidth, viewportHeight, region.pageWidthPoints, region.pageHeightPoints)
        if (dimensions.any { !it.isFinite() || it <= 0 } || !region.bounds.isValid() ||
            region.bounds.right > region.pageWidthPoints || region.bounds.bottom > region.pageHeightPoints) return null
        val ratio = region.pageWidthPoints / region.pageHeightPoints
        // Different axes/rotation must not produce a plausible but misplaced highlight.
        if (abs(imageWidth / imageHeight / ratio - 1f) > .01f) return null
        val scale = min(viewportWidth / imageWidth, viewportHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val x = (viewportWidth - width) / 2f
        val y = (viewportHeight - height) / 2f
        val bounds = region.bounds
        return SourceBounds(x + bounds.left / region.pageWidthPoints * width,
            y + bounds.top / region.pageHeightPoints * height,
            x + bounds.right / region.pageWidthPoints * width,
            y + bounds.bottom / region.pageHeightPoints * height)
    }
}
