package dev.folio.runtime.engineering

import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace

class SourceCorrectionReducer(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, command: AppCommand, actor: CommandActor): Workspace {
        val records = state.sourceCorrections
        val updated = when (command) {
            is AppCommand.ProposeCorrection -> {
                require(catalog.contains(command.source)) { "Unknown source anchor" }
                require(command.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && command.id !in records)
                require(records.size < 1000 && command.replacement.isNotBlank() && command.replacement.length <= 100000)
                val document = catalog.documents().firstOrNull { it.id == command.source.documentId } ?: error("Unknown source document")
                val element = catalog.page(command.source.documentId, command.source.pageIndex)?.elements
                    ?.firstOrNull { it.id == command.source.elementId } ?: error("Unknown source element")
                require(element.kind != SourceElementKind.FIGURE) { "Original figure pixels cannot be corrected as text" }
                require(element.originalText == command.expectedOriginal && command.expectedOriginal.length <= 100000) { "Machine source changed; compare it again" }
                require(command.replacement != element.originalText) { "Correction must differ from machine text" }
                val approved = records.values.singleOrNull { it.source == command.source && it.status == CorrectionStatus.APPROVED }
                require(approved?.id == command.expectedApprovedId) { "Approved correction changed; refresh before proposing" }
                require(approved?.replacement != command.replacement) { "This correction is already approved" }
                require(records.values.count { it.source == command.source && it.status == CorrectionStatus.PROPOSED } < 10)
                val record = SourceCorrection(command.id, command.source, document.checksum, element.originalText, command.replacement,
                    command.expectedApprovedId, if (actor == CommandActor.AI) CorrectionAuthor.AI else CorrectionAuthor.USER)
                records + (record.id to record)
            }
            is AppCommand.ApproveCorrection -> {
                require(actor == CommandActor.USER) { "Only the learner can approve source corrections" }
                val record = records[command.id] ?: error("Unknown correction proposal")
                require(record.status == CorrectionStatus.PROPOSED)
                val document = catalog.documents().firstOrNull { it.id == record.source.documentId } ?: error("Source document unavailable")
                val element = catalog.page(record.source.documentId, record.source.pageIndex)?.elements?.firstOrNull { it.id == record.source.elementId }
                require(catalog.contains(record.source) && document.checksum == record.documentChecksum &&
                    element?.originalText == record.originalText && element.kind != SourceElementKind.FIGURE) { "Source changed; proposal retained for comparison" }
                val approved = records.values.singleOrNull { it.source == record.source && it.status == CorrectionStatus.APPROVED }
                require(approved?.id == record.expectedApprovedId) { "Approval changed; compare the proposal again" }
                records.mapValues { (_, old) -> when {
                    old.id == record.id -> old.copy(status = CorrectionStatus.APPROVED)
                    old.source == record.source && old.status == CorrectionStatus.APPROVED -> old.copy(status = CorrectionStatus.SUPERSEDED)
                    else -> old
                } }
            }
            is AppCommand.RemoveCorrection -> {
                require(actor == CommandActor.USER) { "Only the learner can remove source corrections" }
                val record = records[command.id] ?: error("Unknown correction")
                require(record.status in setOf(CorrectionStatus.PROPOSED, CorrectionStatus.APPROVED))
                records + (record.id to record.copy(status = CorrectionStatus.REMOVED))
            }
            else -> error("Not a correction command")
        }
        require(updated.values.sumOf { it.originalText.length.toLong() + it.replacement.length } <= 2_000_000) { "Correction history storage limit reached" }
        val drafts = if (command is AppCommand.ProposeCorrection && actor == CommandActor.USER) {
            val record = updated.getValue(command.id)
            state.correctionDrafts.filterValues { draft ->
                draft.source != record.source || draft.documentChecksum != record.documentChecksum ||
                    draft.originalText != record.originalText || draft.replacement != record.replacement ||
                    draft.expectedApprovedId != record.expectedApprovedId
            }
        } else state.correctionDrafts
        return state.copy(sourceCorrections = updated, correctionDrafts = drafts)
    }
}
