package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.assistant.VoiceInputState
import dev.folio.ui.icon.*

/** Foreground voice controls stay reachable while an original PDF or workspace is open. */
@Composable
fun VoiceStatusBar(state: VoiceInputState, available: Boolean,
    onStart: () -> Unit, onFinish: () -> Unit, onCancel: () -> Unit, onReview: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(when {
                state.recording -> "Listening · pause when finished"
                state.processing -> "Transcribing speech…"
                state.error != null -> state.error.orEmpty()
                state.transcript.isNotBlank() -> "Last transcript available"
                !available -> "Connect AI in Assistant"
                else -> "Voice ready · speak, then pause"
            }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (state.recording) TextButton(onFinish) { Text("Stop") }
            else if (!state.processing) dev.folio.designsystem.primitive.FolioActionButton("Speak", onStart, enabled = available, prominent = true)
            if (state.recording || state.processing) TextButton(onCancel) { Text("Cancel") }
            else if (state.transcript.isNotBlank() || state.error != null) TextButton(onReview) { Text("Review") }
        }
    }
}
