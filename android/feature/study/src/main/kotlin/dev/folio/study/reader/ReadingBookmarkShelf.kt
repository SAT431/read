package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.ReadingBookmarks
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingBookmarkShelf(activityId: String, source: SourceAnchor, data: ActivityData.Reader, catalog: SourceCatalog,
    onInteraction: (LearningInteraction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ onInteraction(LearningInteraction.AddReadingBookmark(activityId, UUID.randomUUID().toString(), source.copy(elementId = null))) },
            Modifier.testTag("reader-bookmark-position-$activityId"), enabled = catalog.page(source.documentId, source.pageIndex)?.elements?.getOrNull(data.scrollIndex) != null) { Text("Bookmark reading position") }
        TextButton({ onInteraction(LearningInteraction.ShowReadingBookmarks(activityId, !data.bookmarksVisible)) },
            Modifier.testTag("reader-bookmarks-show-$activityId")) { Text("${if (data.bookmarksVisible) "Hide" else "Show"} bookmarks (${data.bookmarks.size})") }
    }
    if (data.bookmarksVisible) {
        if (data.bookmarks.isEmpty()) Text("No reading bookmarks yet.", style = MaterialTheme.typography.bodySmall)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("reader-bookmark-list-$activityId"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.bookmarks.values.sortedByDescending { it.createdAtRevision }, key = { it.id }) { item ->
                val available = runCatching { ReadingBookmarks.resolve(item, catalog) }.isSuccess
                Column {
                    Text("${catalog.documents().firstOrNull { it.id == item.source.documentId }?.title ?: "Unavailable document"} · page ${item.source.pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium)
                    Text(item.excerpt.ifBlank { "Saved reading position" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    if (!available) Text("Saved source target changed or is unavailable.", style = MaterialTheme.typography.bodySmall)
                    if (item.byAssistant) Text("Saved by assistant", style = MaterialTheme.typography.labelSmall)
                    FlowRow {
                        TextButton({ onInteraction(LearningInteraction.OpenReadingBookmark(activityId, item.id, item.createdAtRevision)) }, enabled = available,
                            modifier = Modifier.testTag("reader-bookmark-open-${item.id}")) { Text("Open bookmark") }
                        TextButton({ onInteraction(LearningInteraction.RemoveReadingBookmark(activityId, item.id, item.createdAtRevision)) },
                            Modifier.testTag("reader-bookmark-remove-${item.id}")) { Text("Remove bookmark") }
                    }
                }
            }
        }
    }
}
