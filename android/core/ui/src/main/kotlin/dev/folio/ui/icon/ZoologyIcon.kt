package dev.folio.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

internal fun DrawScope.zoologyIcon(color: Color) {
    for (mirror in listOf(false, true)) {
        val path = Path().apply {
            moveTo(size.width * if (mirror) .7f else .3f, size.height * .1f)
            cubicTo(size.width * if (mirror) .1f else .9f, size.height * .35f,
                size.width * if (mirror) .9f else .1f, size.height * .65f,
                size.width * if (mirror) .3f else .7f, size.height * .9f)
        }
        drawPath(path, color, style = Stroke(3f))
    }
    for (fraction in listOf(.25f, .5f, .75f)) drawLine(color,
        Offset(size.width * .3f, size.height * fraction), Offset(size.width * .7f, size.height * fraction), 2f)
}
