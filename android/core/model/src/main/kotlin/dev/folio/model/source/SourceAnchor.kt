package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable
data class SourceAnchor(val documentId: String, val pageIndex: Int, val elementId: String? = null)

@Serializable
data class SourceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun isValid() = listOf(left, top, right, bottom).all { it.isFinite() } &&
        left >= 0 && top >= 0 && right > left && bottom > top
}
