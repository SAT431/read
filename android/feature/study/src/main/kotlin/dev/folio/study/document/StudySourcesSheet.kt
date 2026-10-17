package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.study.*
import dev.folio.runtime.study.DocumentStudyRules
import dev.folio.ui.control.SemanticButton

/** Source discovery and composition are tools over originals, not edits of their bytes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySourcesSheet(host: LibraryPdf, pdfs: List<LibraryPdf>, maps: Map<String,DocumentOutline>,
    work: Map<String,DocumentStudyWork>, plan: DocumentStudyPlan?, onClose: ()->Unit, dispatch: (AppCommand)->Unit) {
    var sourceId by rememberSaveable(host.id) { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    val source = pdfs.firstOrNull { it.id==sourceId }
    val map = source?.let { maps[it.document.id]?.takeIf { value -> value.checksum==it.document.checksum } }
    val complete = source != null && map?.indexedPages?.size==source.document.pageCount
    var selected by remember(sourceId,map) { mutableStateOf(emptyList<String>()) }
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=LocalConfiguration.current.screenHeightDp.dp*.85f).padding(horizontal=20.dp).testTag("study-source-sheet"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(if(source==null) "PDF sources" else source.document.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextButton(onClose,Modifier.testTag("study-sources-close")) { Text("Close") }
            }
            if(source==null) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if(all) "All imported PDFs" else "This chapter",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                    TextButton({all=!all}) { Text(if(all) "This chapter" else "All PDFs") }
                }
                LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth().testTag("study-source-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(pdfs.filter { all || it.chapterId==host.chapterId },key={it.id}) { pdf ->
                        OutlinedCard(onClick={sourceId=pdf.id;dispatch(AppCommand.HarnessBrowseTopics(pdf.id))},modifier=Modifier.fillMaxWidth().testTag("harness-source-${pdf.id}")) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(pdf.document.title,style=MaterialTheme.typography.titleSmall)
                                Text("${pdf.document.pageCount} pages${if(pdf.id==host.id) " · current study" else ""}${if(plan?.attachedSources?.any { it.pdfId==pdf.id }==true) " · added" else ""}",style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if(pdfs.none { it.id!=host.id && it.chapterId==host.chapterId }) item { Text("Add another PDF to this chapter's library to combine its topics here.",style=MaterialTheme.typography.bodySmall) }
                    plan?.attachedSources?.forEach { link -> item(key="remove-${link.pdfId}") {
                        SemanticButton("harness-remove-${link.pdfId}","Remove added source: ${pdfs.firstOrNull { it.id==link.pdfId }?.document?.title ?: "Unavailable PDF"}",
                            AppCommand.HarnessRemoveSource(host.id,link.pdfId,plan.version),{dispatch(it);onClose()})
                    } }
                }
            } else {
                TextButton({sourceId=null}) { Text("← All sources") }
                val progress = work[source.document.id]
                if(progress?.busy==true) { LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress.message,style=MaterialTheme.typography.bodySmall) }
                progress?.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    SemanticButton("harness-resume-map","Resume analysis",AppCommand.HarnessAnalyzePdf(source.id),dispatch) }
                LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth().testTag("harness-source-topics"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(map?.topics.orEmpty(),key={it.id}) { topic -> StudyTopicRow(source.id,topic,map?.let(DocumentStudyRules::fingerprint).orEmpty(),topic.id in selected,complete,
                        {selected=if(topic.id in selected) selected-topic.id else selected+topic.id},
                        {command -> dispatch(command);if(command is AppCommand.HarnessOpenTopic || command is AppCommand.HarnessInspectTopic) onClose()}) }
                }
                if(source.id!=host.id && plan!=null && plan.pages.isNotEmpty()) SemanticButton("harness-add-topics","Add ${selected.size} ${if(selected.size==1) "topic" else "topics"} to this page",
                    AppCommand.HarnessIncludeTopics(host.id,source.id,plan.version,map?.let(DocumentStudyRules::fingerprint).orEmpty(),selected),
                    {dispatch(it);onClose()},Modifier.fillMaxWidth().padding(bottom=12.dp),enabled=complete && selected.isNotEmpty())
                else Text("Use a topic's menu to read it. Select this study's topics first to combine PDFs.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=12.dp))
            }
        }
    }
}
