package dev.folio.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.folio.model.port.GeminiModels

@Composable
fun ProviderKeyEditor(savedModel: String?, groqConfigured: Boolean, saving: Boolean, error: String?,
    onSave: (String, String, String) -> Unit, onRemove: () -> Unit) {
    var gemini by remember { mutableStateOf("") }
    var groq by remember { mutableStateOf("") }
    // Keep fields owned here even when folded, so toggling the section cannot discard unsaved keys.
    var expanded by rememberSaveable { mutableStateOf(savedModel == null || !groqConfigured) }
    LaunchedEffect(error) { if (error != null) expanded = true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text("Your AI API keys", style = MaterialTheme.typography.titleLarge)
        Text(if (savedModel != null && groqConfigured) "Gemini + Groq connected" else "Connect your study assistant",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton({ expanded = !expanded }, Modifier.testTag("provider-toggle")) { Text(if (expanded) "Hide" else "Manage") }
        }
        if (expanded) {
        Text("Folio calls Gemini and Groq directly. Keys are encrypted on this device with Android Keystore.")
        Text(if (savedModel == null) "No Gemini key saved." else "Gemini configured · $savedModel. Blank key fields keep saved keys.")
        Text(if (groqConfigured) "Groq key saved." else "Add a Groq key for speech transcription.")
        OutlinedTextField(gemini, { gemini = it }, Modifier.fillMaxWidth().testTag("gemini-key"),
            singleLine = true, label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(), enabled = !saving)
        OutlinedTextField(groq, { groq = it }, Modifier.fillMaxWidth().testTag("groq-key"),
            singleLine = true, label = { Text("Groq API key") }, visualTransformation = PasswordVisualTransformation(), enabled = !saving)
        Text("${GeminiModels.DISPLAY_NAME} · ${GeminiModels.CURRENT_FLASH}", Modifier.testTag("gemini-model"))
        Text("Model selected automatically. Free-tier access depends on your Gemini project and quota. No paid fallback.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ onSave(gemini.trim(), groq.trim(), GeminiModels.CURRENT_FLASH); gemini = ""; groq = "" },
                Modifier.testTag("provider-save"), enabled = !saving && (gemini.isNotBlank() || savedModel != null)) { Text("Save API keys") }
            if (savedModel != null || error != null) OutlinedButton(onRemove, enabled = !saving) { Text("Remove saved keys") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
