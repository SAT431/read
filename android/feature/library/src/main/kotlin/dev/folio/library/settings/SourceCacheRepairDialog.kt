package dev.folio.library.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.runtime.source.*
import dev.folio.ui.control.SemanticButton

@Composable
fun SourceCacheRepairDialog(state: SourceCacheRepairState, dispatch: (AppCommand) -> Unit) {
    if (state.phase == SourceCacheRepairPhase.CLOSED) return
    val busy = state.phase in setOf(SourceCacheRepairPhase.CHECKING, SourceCacheRepairPhase.REPAIRING)
    AlertDialog(
        onDismissRequest = { if (!busy) dispatch(AppCommand.DismissSourceCacheRepair) },
        modifier = Modifier.testTag("source-cache-repair-dialog"),
        title = { Text("Readable page cache") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) CircularProgressIndicator()
                when (state.phase) {
                    SourceCacheRepairPhase.CHECKING -> Text("Checking saved readable pages…")
                    SourceCacheRepairPhase.REVIEW -> {
                        val preview = state.preview!!
                        Text("${preview.totalRows} cached page records checked.")
                        Text("${preview.damagedPages} damaged page records · ${preview.damagedReports} damaged comparison reports")
                        Text("Folio will back up every cache record privately before removing damaged pages and clearing damaged comparison reports. Healthy pages stay saved.")
                        Text("Your original PDFs, answers, notes, drafts, bookmarks and reading reports stay saved. Removed pages can be processed again from their PDF when you choose.")
                        Text("Only your tap below approves repair. Gemini cannot approve it.")
                    }
                    SourceCacheRepairPhase.REPAIRING -> Text("Making and checking a private backup, then repairing the cache…")
                    SourceCacheRepairPhase.DONE -> {
                        val receipt = state.receipt
                        if (receipt == null) Text(state.message.orEmpty()) else {
                            Text("Cache repair complete.")
                            Text("${receipt.removedPages} damaged pages removed · ${receipt.clearedReports} comparison reports cleared · ${receipt.retainedPages} pages retained")
                            Text("Private backup saved: ${receipt.backupId}")
                            Text("Open an affected original PDF page and choose Process page to recreate its readable version. Saved learning records keep their original references.")
                        }
                    }
                    SourceCacheRepairPhase.ERROR -> Text(state.message.orEmpty())
                    SourceCacheRepairPhase.CLOSED -> Unit
                }
            }
        },
        confirmButton = {
            if (state.phase == SourceCacheRepairPhase.REVIEW) SemanticButton("source-cache-confirm", "Back up and repair",
                AppCommand.ConfirmSourceCacheRepair(state.confirmationToken!!), dispatch)
            else if (!busy) SemanticButton("source-cache-close", "Close", AppCommand.DismissSourceCacheRepair, dispatch)
        },
        dismissButton = {
            if (state.phase == SourceCacheRepairPhase.REVIEW) SemanticButton("source-cache-cancel", "Cancel", AppCommand.DismissSourceCacheRepair, dispatch)
        }
    )
}
