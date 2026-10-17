package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AnswerOption(text: String, selected: Boolean, submitted: Boolean, correct: Boolean, tag: String, enabled: Boolean = true, onSelect: () -> Unit) {
    OutlinedButton(onSelect, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).semantics {
        this.selected = selected
        stateDescription = when {
            submitted && correct -> "Correct option"
            submitted && selected -> "Selected incorrect option"
            submitted -> "Not selected"
            selected -> "Selected, not checked"
            else -> "Not selected"
        }
    }, enabled = enabled && !submitted,
        colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = when {
            submitted && correct -> MaterialTheme.colorScheme.primary
            submitted && selected -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        })) {
        Text(when {
            submitted && correct -> "✓ $text · correct"
            submitted && selected -> "× $text · your answer"
            selected -> "● $text"
            else -> text
        }, Modifier.fillMaxWidth())
    }
}
