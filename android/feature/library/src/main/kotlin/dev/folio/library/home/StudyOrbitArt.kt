package dev.folio.library.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Native decorative linework: no bitmap downloads, runtime model artwork or motion loops. */
@Composable
internal fun StudyOrbitArt(modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * .94f, size.height * .35f)
        val radius = size.height * .68f
        for (angle in listOf(-35f, 25f, 85f)) rotate(angle, center) {
            drawOval(Color(0xFFBFD3C1).copy(alpha = .11f), center - Offset(radius, radius * .42f),
                Size(radius * 2, radius * .84f), style = Stroke(1.2f))
        }
        drawCircle(Color(0xFFDCCCB5).copy(alpha = .45f), 6f, center + Offset(-radius * .3f, radius * .3f))
    }
}
