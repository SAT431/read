package dev.folio.study.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.folio.model.command.AppCommand
import dev.folio.model.source.*
import dev.folio.ui.interaction.rememberWritingProtection
import java.util.UUID

/** Human approval changes the overlay, never the PDF or machine text. */
@Composable
internal fun SourceCorrectionEditor(source: SourceAnchor, checksum: String, original: String,
    records: List<SourceCorrection>, draft: Map.Entry<String, SourceCorrectionDraft>?, dispatch: (AppCommand) -> Unit) {
    val approved = records.firstOrNull { it.status == CorrectionStatus.APPROVED }
    val active = approved?.takeIf { it.documentChecksum == checksum && it.originalText == original }
    var editing by rememberSaveable(source) { mutableStateOf(draft != null) }
    var draftId by rememberSaveable(source) { mutableStateOf(draft?.key ?: UUID.randomUUID().toString()) }
    var replacement by rememberSaveable(source) { mutableStateOf(draft?.value?.replacement ?: "") }
    var expectedId by rememberSaveable(source) { mutableStateOf(draft?.value?.expectedApprovedId) }
    var expectedOriginal by rememberSaveable(source) { mutableStateOf(draft?.value?.originalText ?: original) }
    var expectedChecksum by rememberSaveable(source) { mutableStateOf(draft?.value?.documentChecksum ?: checksum) }
    var pending by rememberSaveable(source) { mutableStateOf<String?>(null) }
    val protect = rememberWritingProtection(editing)
    fun saveDraft() = dispatch(AppCommand.SaveCorrectionDraft(draftId,
        SourceCorrectionDraft(source, expectedChecksum, expectedOriginal, replacement, expectedId)))
    LaunchedEffect(records) {
        if (pending != null && records.any { it.id == pending }) { editing = false; pending = null; protect(false) }
    }
    Column {
        if (approved != null) {
            if (active == null) Text("Stored approval no longer matches this source. It is retained but not applied.")
            TextButton({ dispatch(AppCommand.RemoveCorrection(approved.id)) }) { Text("Remove approval") }
        }
        if (editing) {
            val current = expectedOriginal == original && expectedChecksum == checksum && expectedId == approved?.id
            if (!current) Text("Source or approval changed while editing. Your draft is retained; copy it before discarding and comparing again.")
            OutlinedTextField(replacement, { if (it.length <= 100000) { replacement = it; protect(true); saveDraft() } },
                Modifier.fillMaxWidth().testTag("correction-draft-${source.elementId}"),
                label = { Text("Proposed wording") }, minLines = 3, maxLines = 8,
                supportingText = { Text("${replacement.length} / 100,000 characters · private local draft") })
            Row {
                TextButton({
                    val id = pending ?: UUID.randomUUID().toString()
                    pending = id
                    dispatch(AppCommand.ProposeCorrection(id, source, expectedOriginal, replacement, expectedId))
                }, enabled = current && replacement.isNotBlank() && replacement.length <= 100000 && replacement != original && replacement != active?.replacement) { Text("Save proposal") }
                TextButton({ dispatch(AppCommand.SaveCorrectionDraft(draftId, null)); editing = false; pending = null; protect(false) }) { Text("Discard correction draft") }
            }
        } else TextButton({
            draftId = UUID.randomUUID().toString(); replacement = active?.replacement ?: original
            expectedOriginal = original; expectedChecksum = checksum; expectedId = approved?.id
            editing = true; protect(true); saveDraft()
        }) { Text("Suggest correction") }
        records.filter { it.status == CorrectionStatus.PROPOSED }.forEach { proposal ->
            val current = proposal.documentChecksum == checksum && proposal.originalText == original && proposal.expectedApprovedId == approved?.id
            Text("Proposed correction · ${proposal.author.name.lowercase()}")
            Text(proposal.replacement, Modifier.testTag("correction-proposal-${proposal.id}"))
            if (!current) Text("Source or approval changed. This proposal cannot be approved.")
            Row {
                TextButton({ dispatch(AppCommand.ApproveCorrection(proposal.id)) }, enabled = current) { Text("Approve correction") }
                TextButton({ dispatch(AppCommand.RemoveCorrection(proposal.id)) }) { Text("Discard proposal") }
            }
        }
        Text("Approving this wording does not verify the whole page.", style = MaterialTheme.typography.labelSmall)
    }
}
