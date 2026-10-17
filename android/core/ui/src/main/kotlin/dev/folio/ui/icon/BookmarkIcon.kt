package dev.folio.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Decorative glyph; the surrounding native button supplies its accessible action label. */
@Composable
fun BookmarkIcon(saved: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * .25f, size.height * .12f)
            lineTo(size.width * .75f, size.height * .12f)
            lineTo(size.width * .75f, size.height * .88f)
            lineTo(size.width * .5f, size.height * .68f)
            lineTo(size.width * .25f, size.height * .88f)
            close()
        }
        drawPath(path, color, style = if (saved) Fill else Stroke(1.5.dp.toPx()))
    }
}
