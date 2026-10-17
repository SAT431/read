package dev.folio.runtime.reading

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.ReadingBookmark
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.*
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.validation.CommandGuards

class ReadingBookmarkReducer(private val catalog: SourceCatalog, private val now: () -> Long = System::currentTimeMillis) {
    fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor): Workspace {
        val id = when (event) {
            is LearningInteraction.AddReadingBookmark -> event.activityId
            is LearningInteraction.RemoveReadingBookmark -> event.activityId
            is LearningInteraction.OpenReadingBookmark -> event.activityId
            is LearningInteraction.ShowReadingBookmarks -> event.activityId
            else -> error("Not a bookmark action")
        }
        val activity = state.activities[id] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Bookmarks require a reader")
        if (event is LearningInteraction.OpenReadingBookmark) {
            val item = reader.bookmarks[event.id] ?: error("Unknown bookmark")
            require(item.createdAtRevision == event.expectedCreatedRevision) { "Bookmark changed; refresh before opening" }
            val index = ReadingBookmarks.resolve(item, catalog)
            return SavedReadingNavigation(catalog).open(state, id, item.source, item.sentenceId, index, item.scrollOffset, actor)
        }
        require(activity.status == ActivityStatus.ACTIVE && state.components.values.any {
            it.activityId == id && it.visible && it.presentation != Presentation.CAPSULE
        }) { "Show and resume the reader first" }
        val next = when (event) {
            is LearningInteraction.ShowReadingBookmarks -> reader.copy(bookmarksVisible = event.visible)
            is LearningInteraction.RemoveReadingBookmark -> {
                require(reader.bookmarkRevision < 1_000_000) { "Bookmark edit limit reached" }
                val item = reader.bookmarks[event.id] ?: error("Unknown bookmark")
                require(item.createdAtRevision == event.expectedCreatedRevision) { "Bookmark changed; refresh before removing" }
                reader.copy(bookmarks = reader.bookmarks - item.id, bookmarkRevision = reader.bookmarkRevision + 1)
            }
            is LearningInteraction.AddReadingBookmark -> {
                require(reader.bookmarkRevision < 1_000_000) { "Bookmark edit limit reached" }
                CommandGuards.id(event.id)
                require(event.id !in reader.bookmarks && reader.bookmarks.size < 200) { "Duplicate bookmark or bookmark limit reached" }
                require(event.source.documentId == activity.source.documentId && event.source.pageIndex == activity.source.pageIndex) { "Bookmark the reader's current page" }
                val page = catalog.page(event.source.documentId, event.source.pageIndex) ?: error("Page unavailable")
                val elements = page.elements.sortedBy { it.order }
                val element = if (event.source.elementId == null) elements.getOrNull(reader.scrollIndex) else elements.firstOrNull { it.id == event.source.elementId }
                requireNotNull(element) { "Bookmark passage unavailable" }
                val source = event.source.copy(elementId = element.id)
                val sentence = event.sentenceId?.let { target -> SentenceIndex().compile(page).firstOrNull { it.id == target && it.source == source }
                    ?: error("Bookmark sentence unavailable") }
                val text = page.corrections[element.id] ?: element.originalText
                val checksum = catalog.documents().firstOrNull { it.id == source.documentId }?.checksum ?: error("Document unavailable")
                val item = ReadingBookmark(event.id, source, sentence?.id,
                    if (event.source.elementId == null && sentence == null) reader.scrollOffset else 0,
                    checksum, ReadingBookmarks.fingerprint(text), (sentence?.text ?: text).take(160), now(), reader.bookmarkRevision + 1, actor == CommandActor.AI)
                require(reader.bookmarks.values.none { it.source == item.source && it.sentenceId == item.sentenceId && it.scrollOffset == item.scrollOffset }) { "This position is already bookmarked" }
                reader.copy(bookmarks = reader.bookmarks + (item.id to item), bookmarkRevision = reader.bookmarkRevision + 1)
            }
            else -> error("Not a bookmark edit")
        }
        ReadingBookmarks.validate(next.bookmarks)
        return state.copy(revision = state.revision + 1, activities = state.activities + (id to activity.copy(data = next)))
    }
}
