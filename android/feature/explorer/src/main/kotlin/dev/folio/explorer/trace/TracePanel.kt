package dev.folio.explorer.trace

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.runtime.command.CommandTrace

@Composable
internal fun TracePanel(traces: List<CommandTrace>) {
    Column {
        Text("Command trace")
        if (traces.isEmpty()) Text("No commands executed in this session")
        traces.take(15).forEach { Text("${if (it.accepted) "Accepted" else "Rejected"}: ${it.commands.joinToString()} ${it.error.orEmpty()}") }
    }
}
