package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.assistant.*

@Composable
fun AssistantScreen(state: AssistantState, savedModel: String?, saving: Boolean, connectionError: String?,
    onSave: (String, String, String) -> Unit, onDisconnect: () -> Unit, onAsk: (String) -> Unit, onCancel: () -> Unit,
    groqConfigured: Boolean = false,
    voiceReviewRequest: Long = 0,
    voiceInput: @Composable () -> Unit = {}) {
    val scroll = rememberLazyListState()
    LaunchedEffect(voiceReviewRequest) { if (voiceReviewRequest > 0) scroll.scrollToItem(2) }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp).testTag("assistant-list"), state = scroll, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Your study assistant", style = MaterialTheme.typography.headlineLarge)
            Text("Study in any order. Actions use the chapter, PDF and page currently open.")
            Text("Clear navigation commands run on your device. Other requests use Gemini.") }
        item { AssistantComposer(savedModel != null && !saving, state.busy,
            state.status == AssistantStatus.EXECUTING, onAsk, onCancel) }
        item { voiceInput() }
        item { Text("Status: ${state.status.name.lowercase()}")
            state.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(state.messages, key = { it.id }) { message ->
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(message.role.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                Text(message.text)
            } }
        }
        item { ProviderKeyEditor(savedModel, groqConfigured, saving || state.busy, connectionError, onSave, onDisconnect) }
    }
}
