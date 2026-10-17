package dev.folio.runtime.source

import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import kotlinx.coroutines.CancellationException

/** Saved references may outlive their source cache; structural validity and availability are distinct. */
object SavedSourceReferences {
    fun validate(source: SourceAnchor) {
        require(source.documentId.isNotBlank() && source.documentId.length <= 200 && source.pageIndex in 0 until Int.MAX_VALUE) { "Invalid saved source reference" }
        require(source.elementId?.let { it.isNotBlank() && it.length <= 200 } != false) { "Invalid saved passage reference" }
    }
    fun all(state: Workspace): List<SourceAnchor> = buildList {
        state.activities.values.forEach { activity ->
            add(activity.source)
            when (val data = activity.data) {
                is ActivityData.Reader -> {
                    addAll(data.annotations.values.map { it.source }); addAll(data.annotationDrafts.values.map { it.source })
                    addAll(data.bookmarks.values.map { it.source }); addAll(data.readingConfirmations.values.map { it.source })
                }
                is ActivityData.Quiz -> addAll((data.questions + data.pastAttempts.flatMap { it.questions }).flatMap { it.sources })
                is ActivityData.Recall -> addAll(data.sources)
                else -> Unit
            }
        }
        state.context.source?.let(::add); addAll(state.context.recentSources); addAll(state.context.chapterCheckpoints.values)
        addAll(state.sourceCorrections.values.map { it.source }); addAll(state.correctionDrafts.values.map { it.source })
        state.revisionItems.values.forEach { item -> add(item.source); addAll(item.completions.mapNotNull { it.source }) }
    }.distinct()

    fun available(source: SourceAnchor, catalog: SourceCatalog): Boolean = try {
        val page = catalog.page(source.documentId, source.pageIndex)
        catalog.contains(source) && page?.documentId == source.documentId && page.pageIndex == source.pageIndex &&
            (source.elementId == null || page.elements.any { it.id == source.elementId })
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }

    fun unavailable(state: Workspace, catalog: SourceCatalog): List<SourceAnchor> = all(state).filterNot { available(it, catalog) }
}
