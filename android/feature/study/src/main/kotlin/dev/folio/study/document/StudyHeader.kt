package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand

@Composable
fun StudyHeader(
    title: String,
    reading: Boolean,
    sourceCount: Int,
    sourcePdfId: String,
    hostPdfId: String,
    onSources: () -> Unit,
    dispatch: (AppCommand) -> Unit
) {
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = { dispatch(AppCommand.Back) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.testTag("study-exit")
        ) {
            Text("← Library", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reading) {
                TextButton(
                    onClick = { dispatch(AppCommand.ShowStudyTopics(hostPdfId)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("study-topic-map")
                ) {
                    Text("Topics", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                TextButton(
                    onClick = { more = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier
                        .testTag("study-tools")
                        .semantics { contentDescription = "Study tools" }
                ) {
                    Text("⋯")
                }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(
                        text = { Text("PDF sources ($sourceCount)") },
                        onClick = { more = false; onSources() },
                        modifier = Modifier.testTag("study-sources")
                    )
                    DropdownMenuItem(
                        text = { Text("Open original PDF") },
                        onClick = { more = false; dispatch(AppCommand.OpenPdf(sourcePdfId)) },
                        modifier = Modifier.testTag("study-original")
                    )
                }
            }
        }
    }
}
