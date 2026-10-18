package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.assistant.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyChatSheet(
    state: AssistantState,
    connected: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onAsk: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    error: String?
) {
    val messages = state.messages.filter { it.role != MessageRole.SYSTEM }.takeLast(20)
    val scroll = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) scroll.scrollToItem(messages.lastIndex)
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.85f)
                .padding(horizontal = 20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Study companion", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClose, Modifier.testTag("study-chat-close")) { Text("Close") }
            }
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("study-chat-history"),
                state = scroll,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "Choose topics, ask for comparison tables or formulas, or ask about what you are reading.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (message.role == MessageRole.USER) "You" else "Folio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.role == MessageRole.ASSISTANT) {
                            StructuredAiMessageContent(rawText = message.text)
                        } else {
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!connected) Text("Configure your API keys in Assistant.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                draft,
                { if (it.length <= 4000) onDraft(it) },
                Modifier
                    .fillMaxWidth()
                    .testTag("study-chat-input"),
                placeholder = { Text("Ask about this topic (tables, formulas, NCERT concepts)…") },
                maxLines = 3
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onAsk,
                    Modifier.testTag("study-chat-send"),
                    enabled = connected && !state.busy && draft.isNotBlank()
                ) { Text("Send") }
                if (state.busy) {
                    TextButton(onCancel, enabled = state.status != AssistantStatus.EXECUTING) {
                        Text("Cancel request")
                    }
                }
            }
        }
    }
}
