package dev.folio.runtime.study

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.study.StudyMode
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.SentenceIndex

class StudySessionOpener(private val executor: CommandExecutor, private val catalog: SourceCatalog?, private val curriculum: CurriculumCatalog) {
    fun open(state: Workspace, host: LibraryPdf, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Workspace {
        val plan = state.studyPlans[host.id]?.takeIf { it.checksum == host.document.checksum }
        val source = pdfs.firstOrNull { plan?.mode==StudyMode.READING && it.id == plan.activeSourcePdfId && plan.attachedSources.any { link -> link.pdfId == it.id && link.checksum == it.document.checksum } } ?: host
        val page = if (plan?.mode == StudyMode.READING) plan.pageIndex.coerceIn(0,source.document.pageCount-1)
            else if (state.navigation.pdfId == host.id && source.id == host.id) state.navigation.pdfPageIndex.coerceIn(0,source.document.pageCount-1) else 0
        val id = "reader-${source.document.id}"
        val focus = if (id in state.activities) WorkspaceCommand.FocusActivity(id) else WorkspaceCommand.CreateActivity(
            id,"$id-view",dev.folio.model.ui.ComponentKind.READER,SourceAnchor(source.document.id,page),ActivityData.Reader())
        val result = executor.execute(state,CommandBatch(requestId="study-open",commands=listOf(focus,
            WorkspaceCommand.OpenChapter("library-${source.id}"),WorkspaceCommand.OpenPage(source.document.id,page))),policy)
        require(result.trace.accepted) { result.trace.error.orEmpty() }
        var next = result.state.copy(navigation=state.navigation.copy(destination=AppDestination.STUDY,
            subject=curriculum.chapters().firstOrNull { it.id == host.chapterId }?.subject ?: state.navigation.subject,
            chapterId=host.chapterId,pdfId=host.id,pdfPageIndex=page,studyOriginalReturn=false,studyReturnPdfId=null))
        if (plan?.guided == true) {
            val sentence = catalog?.page(source.document.id,page)?.let { SentenceIndex().compile(it) }?.firstOrNull { it.id == plan.sentenceId }
            next = if (sentence != null) LearningReducer(catalog).reduce(next,LearningInteraction.FocusSentence(id,sentence.id),policy.actor)
            else next.copy(studyPlans=next.studyPlans + (host.id to plan.copy(guided=false,version=plan.version+1)))
        }
        return next
    }
}
