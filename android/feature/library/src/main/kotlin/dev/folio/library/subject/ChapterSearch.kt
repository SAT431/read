package dev.folio.library.subject

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.folio.model.command.AppCommand
import dev.folio.ui.control.semanticControl

@Composable
internal fun ChapterSearch(query: String, dispatch: (AppCommand) -> Unit) {
    OutlinedTextField(query, { dispatch(AppCommand.SearchChapters(it)) }, Modifier.fillMaxWidth().semanticControl("chapter-search", AppCommand.SearchChapters(query)),
        singleLine = true, label = { Text("Search chapters") })
}
