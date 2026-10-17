package dev.folio.study.reader

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import dev.folio.ui.icon.BookmarkIcon
import dev.folio.model.activity.ActivityData
import dev.folio.model.source.SourceAnchor
import dev.folio.runtime.interaction.LearningInteraction
import java.util.UUID

@Composable
internal fun ReadingBookmarkMarker(activityId: String, source: SourceAnchor, sentenceId: String?, data: ActivityData.Reader,
    onInteraction: (LearningInteraction) -> Unit) {
    val saved = data.bookmarks.values.firstOrNull { it.source == source && it.sentenceId == sentenceId && it.scrollOffset == 0 }
    TextButton({
        if (saved == null) onInteraction(LearningInteraction.AddReadingBookmark(activityId, UUID.randomUUID().toString(), source, sentenceId))
        else onInteraction(LearningInteraction.RemoveReadingBookmark(activityId, saved.id, saved.createdAtRevision))
    }, Modifier.testTag("reader-bookmark-${source.elementId}-${sentenceId ?: "passage"}")) {
        BookmarkIcon(saved != null)
        Spacer(Modifier.width(6.dp))
        Text(if (saved != null) "Remove bookmark" else if (sentenceId == null) "Bookmark passage" else "Bookmark selected sentence")
    }
}
