package dev.folio.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

internal fun DrawScope.chemistryIcon(color: Color) {
    val path = Path().apply {
        moveTo(size.width * .35f, size.height * .1f); lineTo(size.width * .65f, size.height * .1f)
        lineTo(size.width * .65f, size.height * .4f); lineTo(size.width * .85f, size.height * .85f)
        lineTo(size.width * .15f, size.height * .85f); lineTo(size.width * .35f, size.height * .4f); close()
    }
    drawPath(path, color, style = Stroke(3f))
    drawLine(color, Offset(size.width * .25f, size.height * .65f), Offset(size.width * .75f, size.height * .65f), 3f)
}
