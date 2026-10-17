package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.study.DocumentTopic

/** Overview stays one compact row; details and secondary actions open on demand. */
@Composable
fun StudyTopicRow(pdfId: String, topic: DocumentTopic, fingerprint: String, selected: Boolean, enabled: Boolean,
    onSelect: () -> Unit, dispatch: (AppCommand) -> Unit) {
    var details by rememberSaveable(pdfId,topic.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(onClick=onSelect,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("study-topic-${topic.id}"),
        colors=CardDefaults.outlinedCardColors(containerColor=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(selected,null)
                Column(Modifier.weight(1f).padding(horizontal=6.dp)) {
                    Text(topic.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if(topic.hasFigures) Text("Includes visuals",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ dispatch(AppCommand.HarnessInspectTopic(pdfId,fingerprint,topic.id)) },enabled=enabled,
                    modifier=Modifier.testTag("topic-original-${topic.id}")) { Text(if(topic.firstPage==topic.lastPage) "p. ${topic.firstPage+1}" else "pp. ${topic.firstPage+1}–${topic.lastPage+1}") }
                Box {
                    TextButton({menu=true},enabled=enabled,modifier=Modifier.testTag("topic-menu-${topic.id}").semantics{contentDescription="Topic actions: ${topic.title}"}) { Text("⋮") }
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem({Text("Read this topic")},{ menu=false;dispatch(AppCommand.HarnessOpenTopic(pdfId,fingerprint,topic.id)) })
                        DropdownMenuItem({Text(if(details) "Hide details" else "Topic overview")},{menu=false;details=!details})
                    }
                }
            }
            if(details) {
                Text(topic.summary,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(8.dp))
                if(topic.subtopics.isNotEmpty()) Text(topic.subtopics.joinToString(" · "),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(8.dp))
            }
        }
    }
}
