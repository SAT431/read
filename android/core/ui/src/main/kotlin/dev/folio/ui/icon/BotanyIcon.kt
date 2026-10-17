package dev.folio.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

internal fun DrawScope.botanyIcon(color: Color) {
    val path = Path().apply {
        moveTo(size.width * .2f, size.height * .8f)
        cubicTo(0f, size.height * .2f, size.width * .6f, size.height * .1f, size.width * .85f, size.height * .15f)
        cubicTo(size.width * .9f, size.height * .6f, size.width * .6f, size.height, size.width * .2f, size.height * .8f)
    }
    drawPath(path, color, style = Stroke(3f))
    drawLine(color, Offset(size.width * .15f, size.height * .9f), Offset(size.width * .65f, size.height * .35f), 3f)
}
