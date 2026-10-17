package dev.folio.runtime.navigation

import dev.folio.model.activity.ActivityData
import dev.folio.model.activity.ActivityStatus
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace

class SourceNavigator(private val catalog: SourceCatalog) {
    fun open(state: Workspace, anchor: SourceAnchor, chapterId: String? = state.context.chapterId): Workspace {
        require(catalog.contains(anchor)) { "Unknown document, page or element" }
        val activeChapter = catalog.chapters().firstOrNull { it.id == chapterId &&
            it.documentId == anchor.documentId && anchor.pageIndex in it.firstPage..it.lastPage }
        val reader = state.activities[state.context.focusedActivityId]?.takeIf { it.data is ActivityData.Reader }
            ?: state.activities.values.firstOrNull { it.data is ActivityData.Reader }
        val component = reader?.let { a -> state.components.values.firstOrNull { it.activityId == a.id } }
        val pageKey = "${anchor.documentId}:${anchor.pageIndex}"
        val position = (reader?.data as? ActivityData.Reader)?.pagePositions?.get(pageKey)
        return state.copy(context = state.context.copy(chapterId = activeChapter?.id, source = anchor,
            focusedActivityId = reader?.id,
            chapterCheckpoints = if (activeChapter == null) state.context.chapterCheckpoints else
                state.context.chapterCheckpoints + (activeChapter.id to anchor),
            recentSources = (listOf(anchor) + state.context.recentSources).distinct().take(12)),
            activities = if (reader == null) state.activities else state.activities + (reader.id to reader.copy(
                source = anchor, status = ActivityStatus.ACTIVE,
                data = (reader.data as ActivityData.Reader).let { data ->
                    if (reader.source.documentId == anchor.documentId && reader.source.pageIndex == anchor.pageIndex)
                        data.copy(selectedSentence = data.selectedSentence.takeIf { reader.source.elementId == anchor.elementId })
                    else data.copy(selectedSentence = null, scrollIndex = position?.index ?: 0, scrollOffset = position?.offset ?: 0)
                })),
            components = if (component == null) state.components else state.components + (component.id to component.copy(visible = true)),
            focusedComponentId = component?.id ?: state.focusedComponentId)
    }

    fun step(state: Workspace, delta: Int): Workspace {
        val source = state.context.source ?: error("No active document")
        val document = catalog.documents().firstOrNull { it.id == source.documentId } ?: error("Unknown document")
        val chapter = catalog.chapters().firstOrNull { it.id == state.context.chapterId && it.documentId == document.id }
        val target = source.pageIndex + delta
        require(target in (chapter?.firstPage ?: 0)..(chapter?.lastPage ?: document.pageCount - 1)) { "Chapter page boundary reached" }
        return open(state, SourceAnchor(document.id, target))
    }
}
