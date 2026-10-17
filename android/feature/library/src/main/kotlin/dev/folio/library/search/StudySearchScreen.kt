package dev.folio.library.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.search.*
import dev.folio.model.workspace.Workspace
import dev.folio.ui.control.SemanticButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudySearchScreen(workspace: Workspace, snapshot: StudySearchSnapshot?, dispatch: (AppCommand) -> Unit) {
    val nav = workspace.navigation
    LazyColumn(Modifier.fillMaxSize().testTag("study-search"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Search your study", style = MaterialTheme.typography.headlineMedium)
                Text("Chapters, PDFs, cached passages, bookmarks, saved notes and practice prompts. No internet needed.")
                Text("Unprocessed PDF text and unfinished drafts are not included. Search private drafts from Learning → Saved notes and drafts.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(nav.globalQuery, { dispatch(AppCommand.SearchStudy(it.take(200), nav.searchKind)) }, Modifier.fillMaxWidth().testTag("study-search-query"),
                    label = { Text("Search words") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(nav.searchKind == null, { dispatch(AppCommand.SearchStudy(nav.globalQuery)) }, label = { Text("All") }, modifier = Modifier.testTag("search-kind-ALL"))
                    SearchKind.entries.forEach { kind -> FilterChip(nav.searchKind == kind, { dispatch(AppCommand.SearchStudy(nav.globalQuery, kind)) },
                        label = { Text(kind.name.lowercase().replaceFirstChar(Char::titlecase)) }, modifier = Modifier.testTag("search-kind-${kind.name}")) }
                }
                when {
                    nav.globalQuery.isBlank() -> Text("Enter a word or phrase to find saved study material.")
                    snapshot == null -> { CircularProgressIndicator(); Text("Searching saved study material…") }
                    snapshot.error != null -> Text(snapshot.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    else -> Text("${snapshot.total} matching ${if (snapshot.total == 1) "record" else "records"}", Modifier.testTag("study-search-count"))
                }
            }
        }
        if (snapshot != null && snapshot.error == null && nav.globalQuery.isNotBlank()) {
            if (snapshot.total == 0) item("empty") { Text("No saved material matches. Try fewer words or another category.") }
            items(snapshot.results, key = { it.target.toString() }) { result -> SearchResultCard(result, dispatch) }
            if (snapshot.total > 0) item("pages") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Page ${snapshot.page + 1} of ${snapshot.pageCount}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SemanticButton("search-previous", "Previous results", AppCommand.SearchPage((snapshot.page - 1).coerceAtLeast(0)), dispatch, enabled = snapshot.page > 0)
                        SemanticButton("search-next", "Next results", AppCommand.SearchPage(snapshot.page + 1), dispatch, enabled = snapshot.page + 1 < snapshot.pageCount)
                        if (snapshot.page >= snapshot.pageCount) SemanticButton("search-first", "Return to first results", AppCommand.SearchPage(0), dispatch)
                    }
                }
            }
        }
    }
}
