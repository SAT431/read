package dev.folio.study.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.folio.model.port.MediaRepository
import dev.folio.model.source.SourceElement
import dev.folio.ui.media.*

@Composable
internal fun OriginalFigureBlock(source: SourceElement, media: MediaRepository) {
    Column(Modifier.fillMaxWidth().testTag("source-figure-${source.id}")) {
    when (val image = rememberSourceImageState(source.mediaId, media)) {
        is SourceImageState.Ready -> Image(image.bitmap.asImageBitmap(),
            source.originalText.ifBlank { "Original source figure" }, Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("source-figure-ready-${source.id}"))
        SourceImageState.Loading -> Text("Loading original source figure…")
        is SourceImageState.Unavailable -> Text(image.message)
    }
    if (source.originalText.isNotBlank()) Text(source.originalText)
    }
}
