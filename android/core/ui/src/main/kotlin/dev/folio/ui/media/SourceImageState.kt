package dev.folio.ui.media

import android.graphics.Bitmap

sealed interface SourceImageState {
    data object Loading : SourceImageState
    data class Ready(val bitmap: Bitmap) : SourceImageState
    data class Unavailable(val message: String) : SourceImageState
}
