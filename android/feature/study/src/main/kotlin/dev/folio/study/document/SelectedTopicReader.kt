package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.designsystem.theme.LocalReducedMotion
import dev.folio.runtime.study.*
import dev.folio.ui.control.SemanticButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedTopicReader(plan:DocumentStudyPlan,outline:DocumentOutline,catalog:SourceCatalog,
    media:MediaRepository,textSize:Int,sourceRevision:Long,sources:List<StudySource> = emptyList(),dispatch:(AppCommand)->Unit) {
    val active=sources.firstOrNull{it.pdf.id==(plan.activeSourcePdfId ?: plan.pdfId)}
    val references=StudySources.pages(sources)
    val surface=if(sources.isNotEmpty()) StudyComposition.surface(plan,sources) else null
    val sections=surface?.sections?.map{ref -> StudyReadingSection(ref.source.pdf.id,ref.source.pdf.document.id,ref.pageIndex,
        ref.source.pdf.document.title,DocumentStudyRules.topics(ref.source.plan,ref.source.outline))} ?: listOf(
        StudyReadingSection(plan.pdfId,outline.documentId,plan.pageIndex,catalog.documents().firstOrNull{it.id==outline.documentId}?.title.orEmpty(),
            runCatching{DocumentStudyRules.topics(plan,outline)}.getOrDefault(emptyList())))
    val position=if(sources.isEmpty()) plan.pages.indexOf(plan.pageIndex) else references.indexOfFirst{
        it.source.pdf.id==(plan.activeSourcePdfId ?: plan.pdfId) && it.pageIndex==plan.pageIndex
    }
    val total=if(sources.isEmpty()) plan.pages.size else references.size
    fun pageCommand(index:Int):AppCommand=if(sources.isEmpty()) AppCommand.StudyPage(plan.pdfId,plan.version,plan.pages[index]) else
        AppCommand.StudyPage(plan.pdfId,plan.version,references[index].pageIndex,references[index].source.pdf.id)
    val rows=remember(sections,sourceRevision){readingRows(sections,catalog)}
    val scroll=rememberLazyListState()
    val reducedMotion=LocalReducedMotion.current
    var lastTarget by remember{mutableStateOf<String?>(null)}
    val activeKey="${active?.pdf?.document?.id ?: outline.documentId}:${plan.pageIndex}"
    val targetKey=plan.sentenceId?.let{"sentence:$it"} ?: "page:$activeKey"
    LaunchedEffect(targetKey,rows,reducedMotion) {
        // Inserting/loading material must not reset a learner's unchanged reading position.
        if(lastTarget!=targetKey) {
            val target=if(plan.sentenceId!=null) rows.indexOfFirst{it is StudyReadingRow.Content && it.block.sentences.any{line->line.id==plan.sentenceId}}
                else rows.indexOfFirst{it is StudyReadingRow.Source && it.section.key==activeKey}
            if(target>=0) {
                if(reducedMotion) scroll.scrollToItem(target) else scroll.animateScrollToItem(target)
                lastTarget=targetKey
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp).testTag("selected-topic-reader"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.heightIn(min=48.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(position>0) SemanticButton("study-previous-page","Previous",pageCommand(position-1),dispatch)
            Text("Source page ${position+1}/$total · p. ${plan.pageIndex+1}",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelMedium)
            if(position<total-1) SemanticButton("study-next-page","Next",pageCommand(position+1),dispatch)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("study-reading-list"),state=scroll,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            items(rows,key={it.key}) { row -> when(row) {
                is StudyReadingRow.Source -> StudySourceHeader(row,plan,row.section.key!=sections.firstOrNull()?.key,dispatch)
                is StudyReadingRow.Content -> Box(Modifier.fillMaxWidth().testTag("study-block-${row.section.documentId}-${row.section.pageIndex}-${row.block.source.id}")) {
                    StudyReadingContent(row,plan,media,textSize,dispatch)
                }
                is StudyReadingRow.Notice -> Text(row.text,style=MaterialTheme.typography.bodyMedium)
            } }
        }
    }
}
