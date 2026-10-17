package dev.folio.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

internal fun DrawScope.physicsIcon(color: Color) {
    drawOval(color, Offset(size.width * .1f, size.height * .3f), androidx.compose.ui.geometry.Size(size.width * .8f, size.height * .4f), style = Stroke(3f))
    drawOval(color, Offset(size.width * .3f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .4f, size.height * .8f), style = Stroke(3f))
    drawCircle(color, size.minDimension * .08f, center)
}
