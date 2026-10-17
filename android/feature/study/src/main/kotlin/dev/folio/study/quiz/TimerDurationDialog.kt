package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.folio.ui.interaction.LocalInteractionGate

@Composable
internal fun TimerDurationDialog(activityId: String, attempt: Int, initialSeconds: Int?, dismiss: () -> Unit, setLimit: (Int?) -> Unit) {
    var input by rememberSaveable(activityId, attempt) { mutableStateOf(initialSeconds?.toString().orEmpty()) }
    val seconds = input.toIntOrNull()?.takeIf { it in 1..86400 }
    val gate = LocalInteractionGate.current
    val owner = "practice-timer-limit-$activityId-$attempt"
    DisposableEffect(gate, owner) { gate?.set(owner, true); onDispose { gate?.set(owner, false) } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Practice time limit") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set the total duration in seconds. Changing a limit keeps counted time; it does not start or reset the timer.")
            OutlinedTextField(input, { value -> if (value.length <= 5 && value.all(Char::isDigit)) input = value },
                label = { Text("Seconds (1–86400)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.testTag("practice-timer-seconds"))
            TextButton({ setLimit(null) }, Modifier.testTag("practice-timer-stopwatch")) { Text("Use stopwatch") }
        }
    }, confirmButton = { TextButton({ setLimit(seconds) }, enabled = seconds != null,
        modifier = Modifier.testTag("practice-timer-confirm-limit")) { Text("Set limit") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
