package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.study.*
import dev.folio.runtime.study.DocumentStudyRules
import dev.folio.ui.control.SemanticButton

@Composable
fun DocumentTopicsScreen(pdfId: String, outline: DocumentOutline?, plan: DocumentStudyPlan?, work: DocumentStudyWork?, dispatch: (AppCommand) -> Unit) {
    val complete = outline != null && outline.indexedPages.size == outline.pageCount
    val fingerprint = remember(outline) { outline?.let(DocumentStudyRules::fingerprint) }
    val selected = plan?.takeIf { it.outlineFingerprint == fingerprint }?.topicIds.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("document-topics"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Topics",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            Text(if (complete) "${outline!!.topics.size} topics · ${outline.pageCount} pages" else "Analyzing original PDF",style=MaterialTheme.typography.labelMedium)
        }
        Text("Select any topics for today. Page links open the original.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if (work?.busy == true) {
            LinearProgressIndicator(progress={ if (work.total>0) work.completed.toFloat()/work.total else 0f },Modifier.fillMaxWidth())
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(work.message,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                SemanticButton("study-cancel-analysis","Pause",AppCommand.CancelStudyWork(pdfId),dispatch)
            }
        }
        work?.error?.let {
            Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            SemanticButton("study-retry-analysis","Resume analysis",AppCommand.AnalyzeStudy(pdfId),dispatch)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("study-topic-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(outline?.topics.orEmpty(),key={it.id}) { topic ->
                val chosen = topic.id in selected
                StudyTopicRow(pdfId,topic,fingerprint.orEmpty(),chosen,complete,
                    { dispatch(AppCommand.SelectStudyTopics(pdfId,fingerprint.orEmpty(),if(chosen) selected-topic.id else selected+topic.id)) },dispatch)
            }
            if (outline == null && work?.busy != true && work?.error == null) item { Text("Opening saved topic links…") }
        }
        if (plan != null && selected.isNotEmpty()) SemanticButton("study-prepare","Study ${selected.size} topics · ${plan.pages.size} pages",
            AppCommand.BeginStudy(pdfId,plan.version),dispatch,Modifier.fillMaxWidth().padding(bottom=8.dp),enabled=complete)
    }
}
