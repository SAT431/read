package dev.folio.runtime.study

import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.model.workspace.*

/** Shared tool execution for touch and Gemini. All source/plan checks precede effects. */
class PdfHarnessReducer(private val outlines: DocumentOutlineRepository,
    private val openStudy: (Workspace, LibraryPdf, ExecutionPolicy) -> Workspace,
    private val inspect: (Workspace, LibraryPdf, Int) -> Workspace,
    private val study: DocumentStudyReducer) {
    fun apply(state: Workspace, command: AppCommand, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Pair<Workspace,List<AppEffect>> {
        fun pdf(id: String) = pdfs.firstOrNull { it.id == id } ?: error("PDF unavailable")
        fun outline(source: LibraryPdf, fingerprint: String): DocumentOutline {
            val value = outlines.get(source.document.id) ?: error("Analyze this PDF first")
            DocumentStudyRules.validate(value)
            require(value.checksum == source.document.checksum && value.indexedPages.size == source.document.pageCount) { "Finish analyzing this PDF first" }
            require(DocumentStudyRules.fingerprint(value) == fingerprint) { "Topic map changed. Refresh its links." }
            return value
        }
        return when (command) {
            is AppCommand.HarnessInspectPage -> {
                val source=pdf(command.pdfId)
                require(command.pageIndex in 0 until source.document.pageCount){"PDF page unavailable"}
                inspect(state,source,command.pageIndex) to emptyList()
            }
            is AppCommand.HarnessBrowseTopics -> {
                val source = pdf(command.pdfId)
                require(command.query.length <= 200 && command.page in 0..10000)
                val next = state.copy(navigation=state.navigation.copy(harnessPdfId=source.id,harnessTopicQuery=command.query,harnessTopicPage=command.page))
                val map = outlines.get(source.document.id)
                next to if (map?.checksum != source.document.checksum || map.indexedPages.size != source.document.pageCount)
                    listOf(AppEffect.AnalyzeStudy(source.id)) else emptyList()
            }
            is AppCommand.HarnessAnalyzePdf -> { pdf(command.pdfId); state to listOf(AppEffect.AnalyzeStudy(command.pdfId)) }
            is AppCommand.HarnessInspectTopic -> {
                val source = pdf(command.pdfId)
                val topic = outline(source,command.expectedOutline).topics.firstOrNull { it.id == command.topicId } ?: error("Topic unavailable")
                inspect(state,source,topic.firstPage) to emptyList()
            }
            is AppCommand.HarnessOpenTopic -> {
                val source = pdf(command.pdfId); outline(source,command.expectedOutline)
                val opened = openStudy(state,source,policy)
                val selected = study.apply(opened,AppCommand.SelectStudyTopics(source.id,command.expectedOutline,listOf(command.topicId)),pdfs,policy).first
                study.apply(selected,AppCommand.BeginStudy(source.id,selected.studyPlans.getValue(source.id).version),pdfs,policy)
            }
            is AppCommand.HarnessIncludeTopics -> {
                val target = pdf(command.targetPdfId); val source = pdf(command.sourcePdfId)
                require(state.navigation.destination == AppDestination.STUDY && state.navigation.pdfId == target.id) { "Open the destination study first" }
                require(target.id != source.id) { "Use topic selection to change this PDF's own topics" }
                val plan = state.studyPlans[target.id] ?: error("Choose topics in the destination study first")
                StudySources.resolve(plan,pdfs,outlines)
                require(plan.pages.isNotEmpty()) { "Choose destination topics first" }
                require(plan.version == command.expectedVersion) { "Study collection changed. Refresh before adding." }
                val map = outline(source,command.expectedOutline)
                require(command.topicIds.isNotEmpty() && command.topicIds.distinct().size == command.topicIds.size)
                val previous = plan.attachedSources.firstOrNull { it.pdfId == source.id }
                if (previous != null) require(previous.checksum == source.document.checksum && previous.outlineFingerprint == command.expectedOutline) { "Attached source map changed. Remove its reference before reselecting." }
                val ids = (previous?.topicIds.orEmpty() + command.topicIds).distinct()
                val topics = map.topics.filter { it.id in ids }; require(topics.size == ids.size) { "Topic link is unavailable" }
                val link = StudySourceSelection(source.id,source.document.checksum,command.expectedOutline,ids,
                    topics.flatMap { (it.firstPage..it.lastPage).toList() }.distinct().sorted(),
                    if(previous!=null) previous.placement else StudyPlacement(plan.activeSourcePdfId ?: plan.pdfId,plan.pageIndex))
                val updated = plan.copy(attachedSources=plan.attachedSources.filterNot { it.pdfId == source.id } + link,
                    version=plan.version+1,preparationVersion=plan.version+1,mode=StudyMode.READING,guided=false)
                DocumentStudyRules.validatePlans(mapOf(target.id to updated))
                state.copy(studyPlans=state.studyPlans + (target.id to updated)) to listOf(AppEffect.PrepareStudy(target.id,updated.preparationVersion))
            }
            is AppCommand.HarnessRemoveSource -> {
                require(state.navigation.destination == AppDestination.STUDY && state.navigation.pdfId == command.targetPdfId)
                val plan = state.studyPlans[command.targetPdfId] ?: error("Study collection unavailable")
                require(plan.version == command.expectedVersion && plan.attachedSources.any { it.pdfId == command.sourcePdfId }) { "Source reference changed" }
                val removed=plan.attachedSources.first{it.pdfId==command.sourcePdfId}
                val retained=plan.attachedSources.filterNot{it.pdfId==removed.pdfId}.map{link->
                    if(link.placement?.afterPdfId==removed.pdfId) link.copy(placement=removed.placement) else link
                }
                val updated = plan.copy(attachedSources=retained,
                    activeSourcePdfId=null,pageIndex=plan.pages.firstOrNull() ?: 0,sentenceId=null,guided=false,version=plan.version+1,preparationVersion=plan.version+1)
                // Native source focus is restored by the existing validated study opener, never by deleting learner records.
                val next = state.copy(studyPlans=state.studyPlans + (plan.pdfId to updated))
                openStudy(next,pdf(plan.pdfId),policy) to if(updated.mode==StudyMode.READING) listOf(AppEffect.PrepareStudy(plan.pdfId,updated.preparationVersion)) else emptyList()
            }
            else -> error("Unsupported PDF harness tool")
        }
    }
}
