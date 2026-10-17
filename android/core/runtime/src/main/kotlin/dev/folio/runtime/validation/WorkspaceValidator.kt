package dev.folio.runtime.validation

import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.registry.ComponentRegistry

class WorkspaceValidator(private val registry: ComponentRegistry, private val catalog: SourceCatalog) {
    fun unavailableSources(state: Workspace) = dev.folio.runtime.source.SavedSourceReferences.unavailable(state, catalog)
    fun validate(state: Workspace, requireAvailableSources: Boolean = true) {
        dev.folio.runtime.source.SavedSourceReferences.all(state).forEach(dev.folio.runtime.source.SavedSourceReferences::validate)
        dev.folio.runtime.practice.RevisionQueueValidator.validate(state.revisionItems)
        dev.folio.runtime.study.DocumentStudyRules.validatePlans(state.studyPlans)
        require(state.schemaVersion == 1 && state.revision >= 0)
        require(state.correctionDrafts.size <= 20)
        state.correctionDrafts.forEach { (id, draft) ->
            require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            require(draft.source.pageIndex >= 0 && !draft.source.elementId.isNullOrBlank() && draft.source.documentId.isNotBlank())
            require(draft.documentChecksum.length in 1..128 && draft.originalText.length <= 100000 && draft.replacement.length <= 100000)
        }
        require(state.correctionDrafts.values.groupBy { it.source }.values.all { it.size == 1 })
        require(state.correctionDrafts.values.sumOf { it.originalText.length.toLong() + it.replacement.length } <= 1_000_000)
        require(state.sourceCorrections.size <= 1000)
        state.sourceCorrections.forEach { (id, record) ->
            require(id == record.id && id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            require(record.source.pageIndex >= 0 && !record.source.elementId.isNullOrBlank() && record.source.documentId.isNotBlank())
            require(record.documentChecksum.length in 1..128 && record.originalText.length <= 100000 && record.replacement.isNotBlank() && record.replacement.length <= 100000)
        }
        require(state.sourceCorrections.values.filter { it.status == dev.folio.model.source.CorrectionStatus.APPROVED }
            .groupBy { it.source }.values.all { it.size == 1 })
        require(state.sourceCorrections.values.sumOf { it.originalText.length.toLong() + it.replacement.length } <= 2_000_000)
        require(state.preferences.readingSizeSp in 14..36)
        require(state.navigation.pdfPageIndex >= 0 && state.navigation.pdfZoom.isFinite() && state.navigation.pdfZoom in .5f..4f)
        require(state.navigation.globalQuery.length <= 200 && state.navigation.searchPage in 0..1_000_000)
        require(state.navigation.notesQuery.length <= 200)
        require(state.navigation.reviewElementId?.isNotBlank() != false)
        require(state.navigation.pdfPanX.isFinite() && state.navigation.pdfPanY.isFinite())
        require(state.focusedComponentId == null || state.focusedComponentId in state.components)
        state.activities.forEach { (id, activity) ->
            require(id == activity.id); ActivityValidator.validate(activity.data, activity.source, catalog, requireAvailableSources)
        }
        state.components.forEach { (id, component) ->
            require(id == component.id && component.priority in 0..10)
            val activity = state.activities[component.activityId] ?: error("Orphan component")
            val contract = registry.require(component.kind)
            require(ActivityValidator.kind(activity.data) == component.kind)
            require(component.presentation in contract.presentations && component.motion in contract.motions)
        }
        if (requireAvailableSources) state.context.source?.let { require(catalog.contains(it)) }
    }
}
