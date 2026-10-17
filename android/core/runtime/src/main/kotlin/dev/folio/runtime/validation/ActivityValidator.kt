package dev.folio.runtime.validation

import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.source.SourceElementKind
import dev.folio.model.ui.ComponentKind

object ActivityValidator {
    fun kind(data: ActivityData): ComponentKind = when (data) {
        is ActivityData.Reader -> ComponentKind.READER
        is ActivityData.Quiz -> ComponentKind.QUIZ
        is ActivityData.Recall -> ComponentKind.RECALL
        is ActivityData.Explanation -> ComponentKind.EXPLANATION
        is ActivityData.Comparison -> ComponentKind.COMPARISON
        is ActivityData.Diagram -> ComponentKind.DIAGRAM
    }
    fun validate(data: ActivityData, source: SourceAnchor, catalog: SourceCatalog, requireAvailableSources: Boolean = true) {
        dev.folio.runtime.source.SavedSourceReferences.validate(source)
        if (requireAvailableSources) require(catalog.contains(source)) { "Unknown source anchor" }
        when (data) {
            is ActivityData.Recall -> {
                require(data.prompt.isNotBlank() && data.prompt.length <= 4000 && data.referenceAnswer.isNotBlank() && data.referenceAnswer.length <= 20000)
                require(data.sources.size in 1..20)
                data.sources.forEach(dev.folio.runtime.source.SavedSourceReferences::validate)
                if (requireAvailableSources) require(data.sources.all(catalog::contains)) { "Recall has unknown source" }
                require(data.response.length <= 4000 && data.pastAttempts.size <= 100)
                require((data.rating == null) == (data.ratedAtEpochMillis == null))
                require(data.rating == null || data.revealed)
                require(data.ratedAtEpochMillis?.let { it >= 0 } != false)
                require(!data.ratingByAssistant || data.rating != null)
                require(data.pastAttempts.all { it.response.length <= 4000 && it.ratedAtEpochMillis >= 0 })
            }
            is ActivityData.Reader -> {
                dev.folio.runtime.reading.ReadingConfirmations.validate(data)
                dev.folio.runtime.reading.ReadingBookmarks.validate(data.bookmarks)
                require(data.bookmarkJump in 0..1_000_000)
                require(data.bookmarkRevision in 0..1_000_000 && data.bookmarks.values.all { it.createdAtRevision <= data.bookmarkRevision })
                require(data.scrollIndex >= 0 && data.scrollOffset >= 0)
                require(data.pagePositions.values.all { it.index >= 0 && it.offset >= 0 })
                require(data.annotations.size <= 200)
                require(data.annotationDrafts.size <= 200)
                data.annotationDrafts.forEach { (id, draft) ->
                    require(id.matches(Regex("[A-Za-z0-9_-]{1,200}")))
                    require(draft.text.length <= 2000 && (draft.expectedText?.length ?: 0) <= 2000)
                    dev.folio.runtime.source.SavedSourceReferences.validate(draft.source)
                    require(draft.source.elementId != null)
                    if (requireAvailableSources) require(catalog.contains(draft.source.copy(elementId = null)))
                    draft.sentenceId?.let { require(it.length in 1..200) }
                }
                data.annotations.forEach { (id, annotation) ->
                    require(id == annotation.id && id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
                    dev.folio.runtime.source.SavedSourceReferences.validate(annotation.source)
                    require(annotation.source.elementId != null)
                    if (requireAvailableSources) require(catalog.contains(annotation.source.copy(elementId = null)))
                    require(annotation.text.length <= 2000 && (annotation.kind != dev.folio.model.source.AnnotationKind.NOTE || annotation.text.isNotBlank()))
                    annotation.sentenceId?.let { require(it.length in 1..200) }
                }
            }
            is ActivityData.Quiz -> QuizDataValidator.validate(data, catalog, requireAvailableSources)
            is ActivityData.Explanation -> require(data.text.isNotBlank() && data.text.length <= 20_000)
            is ActivityData.Comparison -> {
                require(data.headers.size in 1..8 && data.rows.size in 1..100)
                require(data.rows.all { it.size == data.headers.size }) { "Ragged comparison table" }
            }
            is ActivityData.Diagram -> {
                require(data.zoom.isFinite() && data.zoom in .5f..4f && data.panX.isFinite() && data.panY.isFinite())
                require(source.elementId != null)
                if (requireAvailableSources) require(catalog.page(source.documentId, source.pageIndex)?.elements?.any {
                    it.id == source.elementId && it.kind == SourceElementKind.FIGURE
                } == true) { "Diagram requires a figure anchor" }
            }
        }
    }
}
