package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.source.ReadingProgress

@Composable
internal fun ReadingProgressIndicator(activityId: String, progress: ReadingProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${progress.confirmedPassages} of ${progress.passages} page passages confirmed · ${progress.sentenceCheckpoints} sentence checkpoints",
            Modifier.testTag("reading-progress-$activityId"), style = MaterialTheme.typography.labelMedium)
        if (progress.passages > 0) LinearProgressIndicator(
            progress = { progress.confirmedPassages.toFloat() / progress.passages }, modifier = Modifier.fillMaxWidth())
        else Text("No readable passages available for confirmation.", style = MaterialTheme.typography.bodySmall)
        Text("Based on your explicit reading reports.", style = MaterialTheme.typography.bodySmall)
        if (progress.outdatedCheckpoints > 0) Text("${progress.outdatedCheckpoints} saved checkpoints refer to changed or unavailable wording.",
            style = MaterialTheme.typography.bodySmall)
    }
}
