package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AssistantComposer(connected: Boolean, busy: Boolean, executing: Boolean,
    onAsk: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(draft, { if (it.length <= 4000) draft = it },
            Modifier.fillMaxWidth().testTag("assistant-input"), label = { Text("Ask or control your workspace") },
            placeholder = { Text("Open Botany, open that PDF, or go to the next page…") }, enabled = !busy)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            dev.folio.designsystem.primitive.FolioActionButton("Send", { val text = draft.trim(); draft = ""; onAsk(text) },
                Modifier.testTag("assistant-send"), enabled = connected && !busy && draft.isNotBlank(), prominent = true)
            if (busy) OutlinedButton(onCancel, Modifier.testTag("assistant-cancel"), enabled = !executing) { Text("Cancel request") }
        }
        Text("Speak sends automatically after a pause. Text uses the same study controls.", style = MaterialTheme.typography.bodySmall)
    }
}
