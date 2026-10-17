package dev.folio.runtime.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Independent editors cannot release one another's protection. Never persisted as an active lock. */
class InteractionGate {
    private val owners = MutableStateFlow(emptySet<String>())
    val locked get() = owners.value.isNotEmpty()
    fun blocks(state: dev.folio.model.workspace.Workspace, pdfDocumentId: String? = null): Boolean = locked ||
        (state.navigation.destination == dev.folio.model.workspace.AppDestination.PDF && state.navigation.sourceReviewVisible &&
            state.correctionDrafts.values.any { it.source.documentId == pdfDocumentId && it.source.pageIndex == state.navigation.pdfPageIndex }) ||
        (state.navigation.destination == dev.folio.model.workspace.AppDestination.WORKSPACE && state.components.values.any { component ->
            val activity = state.activities[component.activityId] ?: return@any false
            val reader = activity.data as? dev.folio.model.activity.ActivityData.Reader
            val recall = activity.data as? dev.folio.model.activity.ActivityData.Recall
            component.visible && ((recall != null && !recall.revealed && recall.response.isNotEmpty()) || reader?.annotationDrafts?.values?.any {
                it.source.documentId == activity.source.documentId && it.source.pageIndex == activity.source.pageIndex
            } == true)
        })
    fun set(owner: String, active: Boolean) {
        require(owner.isNotBlank())
        owners.update { if (active) it + owner else it - owner }
    }
}
