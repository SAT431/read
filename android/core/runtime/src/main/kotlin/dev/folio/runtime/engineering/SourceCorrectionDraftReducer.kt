package dev.folio.runtime.engineering

import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace

class SourceCorrectionDraftReducer(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, command: AppCommand.SaveCorrectionDraft, actor: CommandActor): Workspace {
        require(actor == CommandActor.USER) { "Unfinished corrections belong to the local editor" }
        require(command.id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val draft = command.draft
        val old = state.correctionDrafts[command.id]
        if (draft != null) {
            require(draft.originalText.length <= 100000 && draft.replacement.length <= 100000)
            if (old != null) require(draft.copy(replacement = old.replacement) == old) { "Draft source identity cannot change" }
            else {
                require(state.correctionDrafts.size < 20 && state.correctionDrafts.values.none { it.source == draft.source })
                val document = catalog.documents().firstOrNull { it.id == draft.source.documentId }
                val element = catalog.page(draft.source.documentId, draft.source.pageIndex)?.elements?.firstOrNull { it.id == draft.source.elementId }
                require(catalog.contains(draft.source) && document?.checksum == draft.documentChecksum &&
                    element?.originalText == draft.originalText && element.kind != SourceElementKind.FIGURE) { "Source changed; compare again" }
                require(state.sourceCorrections.values.singleOrNull {
                    it.source == draft.source && it.status == CorrectionStatus.APPROVED
                }?.id == draft.expectedApprovedId) { "Approval changed; compare again" }
            }
        }
        val updated = if (draft == null) state.correctionDrafts - command.id else state.correctionDrafts + (command.id to draft)
        require(updated.values.sumOf { it.originalText.length.toLong() + it.replacement.length } <= 1_000_000) { "Unfinished correction storage limit reached" }
        return state.copy(correctionDrafts = updated)
    }
}
