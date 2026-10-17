package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.ui.icon.*

@Composable
fun VoiceInputPanel(connected: Boolean, recording: Boolean, processing: Boolean, transcript: String, error: String?,
    onStart: () -> Unit, onFinish: () -> Unit, onCancel: () -> Unit, onEdit: (String) -> Unit, onSend: () -> Unit,
    automatic: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Voice input", style = MaterialTheme.typography.titleLarge)
        Text(if (recording) "Listening · pause when finished; stops automatically" else if (processing) "Transcribing with Groq Whisper Large v3…"
            else if (automatic) "Tap Speak, talk, then pause. Sends automatically." else "Record, review your words, then send.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(if (recording) onFinish else onStart, Modifier.testTag("voice-record"), enabled = recording || (connected && !processing)) {
                FolioIcon(IconId.MICROPHONE); Text(if (recording) "Stop and transcribe" else "Speak")
            }
            if (recording || processing) OutlinedButton(onCancel) { Text("Cancel voice input") }
        }
        if (transcript.isNotEmpty()) {
            OutlinedTextField(transcript, onEdit, Modifier.fillMaxWidth().testTag("voice-transcript"), label = { Text("Review transcript") })
            Button(onSend, Modifier.testTag("voice-send"), enabled = connected && !recording && !processing && transcript.isNotBlank()) { Text("Send to assistant") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
