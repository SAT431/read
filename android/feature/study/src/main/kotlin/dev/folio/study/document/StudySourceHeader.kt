package dev.folio.study.document

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.study.DocumentStudyPlan
import dev.folio.ui.control.semanticControl

@Composable
internal fun StudySourceHeader(row:StudyReadingRow.Source,plan:DocumentStudyPlan,added:Boolean,dispatch:(AppCommand)->Unit) {
    val section=row.section
    if(added) HorizontalDivider(Modifier.padding(top=12.dp))
    Row(Modifier.fillMaxWidth().testTag("study-inline-source-${section.pdfId}-${section.pageIndex}"),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        val focus=AppCommand.StudyPage(plan.pdfId,plan.version,section.pageIndex,section.pdfId)
        TextButton({dispatch(focus)},Modifier.weight(1f).semanticControl("study-source-focus-${section.pdfId}-${section.pageIndex}",focus),enabled=row.status!=null,
            contentPadding=PaddingValues(horizontal=0.dp,vertical=4.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("${if(added) "Added from · " else ""}${section.title} · p. ${section.pageIndex+1}",style=MaterialTheme.typography.labelMedium)
                Text("Original-linked · ${row.status ?: "preparing"}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val original=AppCommand.HarnessInspectPage(section.pdfId,section.pageIndex)
        TextButton({dispatch(original)},Modifier.semanticControl("study-source-original-${section.pdfId}-${section.pageIndex}",original)) {Text("Original")}
    }
}
