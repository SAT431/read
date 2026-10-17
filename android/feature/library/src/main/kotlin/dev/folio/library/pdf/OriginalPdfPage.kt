package dev.folio.library.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.AppNavigation
import dev.folio.model.source.SourceRegion
import dev.folio.runtime.engineering.SourceRegions
import dev.folio.ui.gesture.registeredTransform
import dev.folio.ui.media.*

/** Shared original-pixel viewport for reading and extraction comparison. */
@Composable
fun OriginalPdfPage(pdf: LibraryPdf, navigation: AppNavigation, tools: PdfTools, media: MediaRepository,
    dispatch: (AppCommand) -> Unit, region: SourceRegion? = null) {
    var mediaId by remember(pdf.id, navigation.pdfPageIndex) { mutableStateOf<String?>(null) }
    var error by remember(pdf.id, navigation.pdfPageIndex) { mutableStateOf<String?>(null) }
    LaunchedEffect(pdf.id, navigation.pdfPageIndex) {
        try { mediaId = tools.render(pdf.document.id, navigation.pdfPageIndex) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Unable to render the original PDF page." }
    }
    val image = rememberSourceImageState(mediaId, media)
    val current by rememberUpdatedState(navigation)
    when {
        error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        mediaId == null || image is SourceImageState.Loading -> Column { CircularProgressIndicator(); Text("Rendering original source…") }
        image is SourceImageState.Unavailable -> Text(image.message, color = MaterialTheme.colorScheme.error)
        image is SourceImageState.Ready -> BoxWithConstraints(Modifier.fillMaxSize().testTag("original-pdf-viewport").clipToBounds().registeredTransform { zoom, x, y ->
            dispatch(AppCommand.PdfTransform((current.pdfZoom * zoom).coerceIn(.5f, 4f),
                (current.pdfPanX + x).coerceIn(-2000f, 2000f), (current.pdfPanY + y).coerceIn(-2000f, 2000f)))
        }) {
            val selected = region?.takeIf { it.source.documentId == pdf.document.id && it.source.pageIndex == navigation.pdfPageIndex }
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = navigation.pdfZoom; scaleY = navigation.pdfZoom
                translationX = navigation.pdfPanX; translationY = navigation.pdfPanY
            }) {
                Image(image.bitmap.asImageBitmap(), "Original PDF page ${navigation.pdfPageIndex + 1}", Modifier.fillMaxSize())
                if (selected != null && SourceRegions.project(selected, image.bitmap.width.toFloat(), image.bitmap.height.toFloat(), 1f, 1f) != null)
                    OriginalRegionOverlay(selected, image.bitmap.width, image.bitmap.height)
            }
            if (selected != null && SourceRegions.project(selected, image.bitmap.width.toFloat(), image.bitmap.height.toFloat(), 1f, 1f) == null)
                Surface(Modifier.align(Alignment.BottomCenter)) { Text("Original region unavailable: page dimensions differ.") }
            val widthZoom = ((maxWidth.value / maxHeight.value) / (image.bitmap.width.toFloat() / image.bitmap.height)).coerceIn(1f, 4f)
            val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            if(navigation.pdfZoom>1.05f) dev.folio.ui.control.SemanticButton("pdf-fit-page","Fit page",AppCommand.PdfTransform(1f,0f,0f),dispatch,
                Modifier.align(Alignment.TopEnd))
            else if (widthZoom.isFinite() && widthZoom > 1.05f) dev.folio.ui.control.SemanticButton("pdf-fit-width", "Fit width",
                AppCommand.PdfTransform(widthZoom, 0f, (heightPx * (widthZoom - 1) / 2).coerceIn(-2000f, 2000f)), dispatch,
                Modifier.align(Alignment.TopEnd))
        }
    }
}
