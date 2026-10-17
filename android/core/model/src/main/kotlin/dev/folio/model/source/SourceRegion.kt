package dev.folio.model.source

/** A region in the same page-point coordinates used for original-page rendering/crops. */
data class SourceRegion(val source: SourceAnchor, val bounds: SourceBounds,
    val pageWidthPoints: Float, val pageHeightPoints: Float)
