package dev.folio.ui.control

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import dev.folio.designsystem.token.FolioTokens
import dev.folio.model.command.AppCommand
import dev.folio.runtime.action.AppActionRegistry

val SemanticActionId = SemanticsPropertyKey<String>("FolioActionId")
val SemanticControlId = SemanticsPropertyKey<String>("FolioControlId")

fun Modifier.semanticControl(id: String, command: AppCommand) = testTag(id).semantics {
    this[SemanticControlId] = id; this[SemanticActionId] = AppActionRegistry.id(command)
}

@Composable
fun SemanticButton(id: String, label: String, command: AppCommand, dispatch: (AppCommand) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    dev.folio.designsystem.primitive.FolioActionButton(label, { dispatch(command) },
        modifier.semanticControl(id, command), enabled = enabled)
}
