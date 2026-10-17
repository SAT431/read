package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand

@Composable
fun StudyHeader(title: String, reading: Boolean, sourceCount: Int, sourcePdfId: String, hostPdfId: String,
    onSources: ()->Unit, dispatch: (AppCommand)->Unit) {
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        TextButton({dispatch(AppCommand.Back)},Modifier.testTag("study-exit")) { Text("← Library") }
        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
            Text(title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
            if(sourceCount>1) Text("$sourceCount linked PDFs",style=MaterialTheme.typography.labelSmall)
        }
        if(reading) TextButton({dispatch(AppCommand.ShowStudyTopics(hostPdfId))},Modifier.testTag("study-topic-map")) { Text("Topics") }
        Box {
            TextButton({more=true},Modifier.testTag("study-tools").semantics{contentDescription="Study tools"}) { Text("⋯") }
            DropdownMenu(more,{more=false}) {
                DropdownMenuItem({Text("PDF sources")},{more=false;onSources()},Modifier.testTag("study-sources"))
                DropdownMenuItem({Text("Open original PDF")},{more=false;dispatch(AppCommand.OpenPdf(sourcePdfId))},Modifier.testTag("study-original"))
            }
        }
    }
}
