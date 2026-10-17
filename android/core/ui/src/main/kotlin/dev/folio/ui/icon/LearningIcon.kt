package dev.folio.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
internal fun LearningIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        for (row in listOf(.25f, .5f, .75f)) {
            drawCircle(color, radius = size.width * .05f, center = Offset(size.width * .15f, size.height * row))
            drawLine(color, Offset(size.width * .32f, size.height * row), Offset(size.width * .88f, size.height * row), strokeWidth = size.width * .07f)
        }
    }
}
