package dev.folio.study.diagram

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.activity.ActivityData
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.component.*
import dev.folio.ui.gesture.registeredTransform
import dev.folio.ui.media.rememberSourceImage

class DiagramRenderer : RegisteredRenderer {
    override val kind = ComponentKind.DIAGRAM
    @Composable override fun Render(context: ComponentRenderContext) {
        val data = context.activity.data as ActivityData.Diagram
        val element = context.catalog.page(context.activity.source.documentId, context.activity.source.pageIndex)
            ?.elements?.firstOrNull { it.id == context.activity.source.elementId && it.kind == dev.folio.model.source.SourceElementKind.FIGURE }
        val bitmap = rememberSourceImage(element?.mediaId, context.media)
        val current by rememberUpdatedState(data)
        Box(Modifier.fillMaxWidth().height(280.dp).registeredTransform { zoom, x, y ->
            context.onInteraction(LearningInteraction.TransformDiagram(context.activity.id,
                current.zoom * zoom, current.panX + x, current.panY + y))
        }) {
            val image = bitmap
            if (image == null) Text("The original figure is unavailable. Your saved view settings are preserved.")
            else Image(image.asImageBitmap(), element?.originalText ?: "Original source figure",
                Modifier.fillMaxSize().graphicsLayer { scaleX = data.zoom; scaleY = data.zoom; translationX = data.panX; translationY = data.panY })
        }
        Text("Zoom ${(data.zoom * 100).toInt()}% · pinch or use controls")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolioActionButton("Zoom in", { context.onInteraction(LearningInteraction.TransformDiagram(context.activity.id, data.zoom + .25f, data.panX, data.panY)) })
            FolioActionButton("Reset", { context.onInteraction(LearningInteraction.TransformDiagram(context.activity.id, 1f, 0f, 0f)) })
        }
    }
}
