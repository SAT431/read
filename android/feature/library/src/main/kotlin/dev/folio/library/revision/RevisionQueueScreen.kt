package dev.folio.library.revision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.*
import kotlinx.coroutines.delay

/** A local review queue; reported completion does not measure mastery. No background alarms. */
@Composable
fun RevisionQueueScreen(state: Workspace, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit,
    nowEpochMillis: Long? = null) {
    val now by produceState(nowEpochMillis ?: System.currentTimeMillis(), nowEpochMillis) {
        if (nowEpochMillis == null) while (true) { delay(60_000); value = System.currentTimeMillis() }
    }
    val all = state.revisionItems.values
    val due = all.filter { !it.done && it.dueAtEpochMillis <= now }.sortedWith(compareBy({ it.dueAtEpochMillis }, { it.id }))
    val upcoming = all.filter { !it.done && it.dueAtEpochMillis > now }.sortedWith(compareBy({ it.dueAtEpochMillis }, { it.id }))
    val completed = all.filter { it.done }.sortedWith(compareByDescending<dev.folio.model.activity.RevisionItem> { it.updatedAtEpochMillis }.thenBy { it.id })
    LazyColumn(Modifier.fillMaxSize().testTag("revision-queue"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Revision", style = MaterialTheme.typography.headlineMedium)
                Text("${due.size} due · ${upcoming.size} upcoming · ${completed.size} completed")
                Text("Choose what to study in any order. Mark reviewed only after your review; this records your report, not mastery.")
                Text("This queue updates while open. It does not send Android notifications.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (all.isEmpty()) item("empty") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No reviews scheduled. Use Review later on a quiz question or active recall card.")
                OutlinedButton({ dispatch(AppCommand.Navigate(AppDestination.WORKSPACE)) }) { Text("Open workspace") }
            }
        }
        for ((label, section) in listOf("Due now" to due, "Upcoming" to upcoming, "Completed" to completed)) {
            if (section.isNotEmpty()) item("heading-$label") { Text(label, style = MaterialTheme.typography.titleLarge) }
            items(section, key = { it.id }) { RevisionQueueCard(it, state, catalog, dispatch) }
        }
    }
}
