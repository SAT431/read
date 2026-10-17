package dev.folio.library.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.folio.model.source.SourceRegion
import dev.folio.runtime.engineering.SourceRegions

/** Lives in the image's transform layer, so highlighting cannot drift during zoom/pan. */
@Composable
internal fun OriginalRegionOverlay(region: SourceRegion, imageWidth: Int, imageHeight: Int) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize().testTag("original-region-overlay").semantics {
        contentDescription = "Original source region ${region.source.elementId}, page ${region.source.pageIndex + 1}"
    }) {
        val bounds = SourceRegions.project(region, imageWidth.toFloat(), imageHeight.toFloat(), size.width, size.height) ?: return@Canvas
        val offset = Offset(bounds.left, bounds.top)
        val rectangle = Size(bounds.right - bounds.left, bounds.bottom - bounds.top)
        drawRect(color.copy(alpha = .18f), offset, rectangle)
        drawRect(color, offset, rectangle, style = Stroke(2.dp.toPx()))
    }
}
