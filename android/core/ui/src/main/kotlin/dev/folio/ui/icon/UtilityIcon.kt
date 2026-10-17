package dev.folio.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*

@Composable
internal fun UtilityIcon(id: IconId) {
    if (id == IconId.LEARNING) { LearningIcon(); return }
    if (id == IconId.BOOKMARK) { BookmarkIcon(); return }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            fun line(x: Float, y: Float, ex: Float, ey: Float) = drawLine(color, Offset(x, y), Offset(ex, ey), 1.6f)
            fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(2f), style = Stroke(1.6f))
            when (id) {
                IconId.HOME -> { drawPath(Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f) }, color, style = Stroke(1.6f))
                    box(5f, 10f, 14f, 11f); box(10f, 15f, 4f, 6f) }
                IconId.WORKSPACE -> { box(3f, 3f, 7f, 18f); box(13f, 3f, 8f, 8f); box(13f, 14f, 8f, 7f) }
                IconId.PDF, IconId.LIBRARY -> { box(5f, 2f, 14f, 20f); line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 13f, 16f) }
                IconId.ASSISTANT, IconId.EXPLORER -> drawPath(Path().apply {
                    moveTo(12f, 2f); lineTo(15f, 9f); lineTo(22f, 12f); lineTo(15f, 15f)
                    lineTo(12f, 22f); lineTo(9f, 15f); lineTo(2f, 12f); lineTo(9f, 9f); close()
                }, color, style = Stroke(1.6f))
                IconId.MICROPHONE -> { box(9f, 3f, 6f, 12f); drawArc(color, 0f, 180f, false, Offset(5f, 7f), Size(14f, 12f), style = Stroke(1.6f))
                    line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f) }
                IconId.REVISION -> { drawArc(color, 40f, 290f, false, Offset(3f, 3f), Size(18f, 18f), style = Stroke(1.6f))
                    line(20f, 2f, 20f, 8f); line(14f, 8f, 20f, 8f) }
                IconId.SETTINGS -> { for (x in listOf(5f, 12f, 19f)) line(x, 3f, x, 21f)
                    for ((x, y) in listOf(5f to 8f, 12f to 16f, 19f to 7f)) drawCircle(color, 2.8f, Offset(x, y), style = Stroke(1.6f)) }
                IconId.BACK -> { line(20f, 12f, 4f, 12f); line(4f, 12f, 10f, 6f); line(4f, 12f, 10f, 18f) }
                IconId.ADD -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
                else -> drawCircle(color, 8f, Offset(12f, 12f), style = Stroke(1.6f))
            }
        }
    }
}
