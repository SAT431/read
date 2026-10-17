package dev.folio.library.chapter

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.designsystem.primitive.FolioSurface
import dev.folio.model.command.AppCommand
import dev.folio.ui.control.SemanticButton
import dev.folio.ui.icon.*

@Composable
internal fun EmptyLibrary(chapterId: String, dispatch: (AppCommand) -> Unit) {
    FolioSurface {
        FolioIcon(IconId.LIBRARY)
        Text("Your chapter library starts here.")
        Text("Add an NCERT chapter PDF, textbook PDF or your notes. Original viewing works locally; AI page engineering connects in the next phase.")
        SemanticButton("empty-import-$chapterId", "Add a PDF", AppCommand.RequestPdfImport(chapterId), dispatch)
    }
}
