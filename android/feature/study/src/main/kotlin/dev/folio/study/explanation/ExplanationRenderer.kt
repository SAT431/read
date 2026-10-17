package dev.folio.study.explanation

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.ComponentKind
import dev.folio.ui.component.*

class ExplanationRenderer : RegisteredRenderer {
    override val kind = ComponentKind.EXPLANATION
    @Composable override fun Render(context: ComponentRenderContext) {
        Text("Enrichment · linked to page ${context.activity.source.pageIndex + 1}")
        SelectionContainer { Text((context.activity.data as ActivityData.Explanation).text) }
        FolioActionButton("Return to source", { context.onCommands(listOf(WorkspaceCommand.FocusSource(context.activity.source))) })
    }
}
