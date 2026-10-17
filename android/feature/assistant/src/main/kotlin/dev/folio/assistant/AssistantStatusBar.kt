package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.assistant.*

/** Stays visible after a successful command opens another app destination. */
@Composable
fun AssistantStatusBar(state: AssistantState, onCancel: () -> Unit, onRetry: () -> Unit) {
    if (state.status == AssistantStatus.IDLE || state.status == AssistantStatus.COMPLETED) return
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Assistant: ${state.status.name.lowercase()}${if (state.busy && state.round > 1) " · step ${state.round} of 3" else ""}${state.error?.let { " · $it" }.orEmpty()}", Modifier.weight(1f))
            if (state.status == AssistantStatus.REQUESTING || state.status == AssistantStatus.PREPARING) TextButton(onCancel) { Text("Cancel") }
            if (state.status == AssistantStatus.FAILED && state.retryUtterance != null)
                TextButton(onRetry) { Text(if (state.acceptedActions.isEmpty()) "Retry command" else "Continue request") }
        }
    }
}
