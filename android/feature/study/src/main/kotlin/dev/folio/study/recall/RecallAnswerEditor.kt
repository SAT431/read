package dev.folio.study.recall

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import dev.folio.model.activity.ActivityData
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.interaction.rememberWritingProtection

@Composable
internal fun RecallAnswerEditor(activityId: String, recall: ActivityData.Recall, onInteraction: (LearningInteraction) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val protect = rememberWritingProtection(!recall.revealed && (focused || recall.response.isNotEmpty()))
    OutlinedTextField(recall.response, { text ->
        if (text.length <= 4000) {
            protect(true)
            onInteraction(LearningInteraction.RecallResponse(activityId, recall.response, text))
        }
    }, Modifier.fillMaxWidth().testTag("recall-response-$activityId").onFocusChanged {
        focused = it.isFocused; protect(!recall.revealed && (focused || recall.response.isNotEmpty()))
    }, readOnly = recall.revealed, label = { Text("Your recall response (optional)") }, minLines = 3, maxLines = 8,
        supportingText = { Text("${recall.response.length} / 4,000 characters · ${if (recall.revealed) "locked after reveal" else "private local draft"}") })
    if (recall.responseByAssistant) Text("Response entered through assistant.", style = MaterialTheme.typography.labelSmall)
}
