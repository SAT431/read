package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.ui.control.SemanticButton

@Composable
fun PageEngineeringControls(documentId: String, pageIndex: Int, ready: Boolean, busy: Boolean,
    message: String?, fidelity: String?, dispatch: (AppCommand) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!ready) SemanticButton("engineer-$documentId-$pageIndex", "Process page ${pageIndex + 1}",
            AppCommand.EngineerPage(documentId, pageIndex), dispatch, enabled = !busy)
        if (message != null) Text(message)
        if (ready) Text("Cached source reconstruction · unchecked")
        if (ready) SemanticButton("review-$documentId-$pageIndex", "Compare extraction", AppCommand.ReviewPage(documentId, pageIndex), dispatch)
        if (fidelity != null) Text(fidelity)
    }
}
