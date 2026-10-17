package dev.folio.runtime.engineering

import dev.folio.model.source.SourceBounds

/** Conservative page-width strip preserves labels outside an approximate model figure box. */
object FigureCropPolicy {
    fun context(bounds: SourceBounds,width: Float,height: Float): SourceBounds {
        require(width.isFinite() && height.isFinite() && width>0 && height>0 && bounds.isValid())
        require(bounds.right<=width && bounds.bottom<=height)
        val margin=maxOf(16f,height*.04f)
        return SourceBounds(0f,maxOf(0f,bounds.top-margin),width,minOf(height,bounds.bottom+margin))
    }
}
