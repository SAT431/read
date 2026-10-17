package dev.folio.runtime.reading

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.*
import dev.folio.runtime.validation.CommandGuards

/** Callers first validate their saved source evidence; this shares resume/pin/position semantics. */
class SavedReadingNavigation(private val catalog: SourceCatalog) {
    fun open(state: Workspace, activityId: String, source: SourceAnchor, sentenceId: String?, index: Int,
        offset: Int, actor: CommandActor): Workspace {
        require(index >= 0 && offset >= 0)
        val activity = state.activities[activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Saved reading target requires a reader")
        val view = state.components.values.firstOrNull { it.activityId == activityId } ?: error("Reader view unavailable")
        state.components.values.filter { it.activityId == activityId }.forEach { CommandGuards.mayRearrange(it, ExecutionPolicy(actor)) }
        require(reader.bookmarkJump < 1_000_000) { "Saved reading navigation limit reached" }
        val next = reader.copy(selectedSentence = sentenceId, scrollIndex = index, scrollOffset = offset,
            pagePositions = reader.pagePositions + ("${source.documentId}:${source.pageIndex}" to ReadingPosition(index, offset)),
            bookmarkJump = reader.bookmarkJump + 1)
        val chapter = catalog.chapters().firstOrNull { it.documentId == source.documentId && source.pageIndex in it.firstPage..it.lastPage }
        return state.copy(revision = state.revision + 1, activities = state.activities + (activityId to activity.copy(
            source = source, data = next, status = ActivityStatus.ACTIVE)),
            components = state.components + (view.id to view.copy(visible = true,
                presentation = if (view.presentation == Presentation.CAPSULE) Presentation.SURFACE else view.presentation)),
            focusedComponentId = view.id, context = state.context.copy(source = source, focusedActivityId = activityId,
                chapterId = chapter?.id, chapterCheckpoints = if (chapter == null) state.context.chapterCheckpoints else
                    state.context.chapterCheckpoints + (chapter.id to source),
                recentSources = (listOf(source) + state.context.recentSources).distinct().take(12)),
            navigation = state.navigation.copy(destination = AppDestination.WORKSPACE))
    }
}
