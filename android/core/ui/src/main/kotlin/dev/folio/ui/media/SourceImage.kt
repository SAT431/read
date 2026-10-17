package dev.folio.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import dev.folio.model.port.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared bounded decoder. Repository owns retrieval; feature renderer owns presentation only. */
@Composable
fun rememberSourceImageState(mediaId: String?, media: MediaRepository): SourceImageState {
    val sourceRevision = LocalSourceRevision.current
    return key(mediaId, media) {
        val result by produceState<SourceImageState>(SourceImageState.Loading,sourceRevision) {
            // Assets are immutable/content-addressed. An unrelated page cache write must not
            // replace a visible image with a loading label and move the reading viewport.
            if(value is SourceImageState.Ready) return@produceState
            value = withContext(Dispatchers.IO) {
                if (mediaId == null) return@withContext SourceImageState.Unavailable("No source image attached")
                val bytes = try { media.read(mediaId) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null } ?: return@withContext SourceImageState.Unavailable("Source image could not be loaded")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext SourceImageState.Unavailable("Invalid source image")
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (bounds.outWidth / options.inSampleSize > 4096 || bounds.outHeight / options.inSampleSize > 4096) options.inSampleSize *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(SourceImageState::Ready)
                    ?: SourceImageState.Unavailable("Source image could not be decoded")
            }
        }
        result
    }
}
@Composable
fun rememberSourceImage(mediaId: String?, media: MediaRepository): Bitmap? =
    (rememberSourceImageState(mediaId, media) as? SourceImageState.Ready)?.bitmap
