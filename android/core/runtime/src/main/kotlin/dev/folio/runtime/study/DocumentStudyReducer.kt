package dev.folio.runtime.study

import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor

class DocumentStudyReducer(private val outlines: DocumentOutlineRepository, private val catalog: SourceCatalog,
    executor: CommandExecutor) {
    private val guide = StudyGuideReducer(catalog, executor)
    private val focus = StudyTargetFocus(executor, catalog)
    fun apply(state: Workspace, command: AppCommand, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Pair<Workspace, List<AppEffect>> {
        val pdfId = when (command) {
            is AppCommand.AnalyzeStudy -> command.pdfId; is AppCommand.SelectStudyTopics -> command.pdfId
            is AppCommand.CancelStudyWork -> command.pdfId
            is AppCommand.BeginStudy -> command.pdfId; is AppCommand.ShowStudyTopics -> command.pdfId
            is AppCommand.GuideStudy -> command.pdfId; is AppCommand.FinishStudyLine -> command.pdfId
            is AppCommand.StudyPage -> command.pdfId; is AppCommand.StudySentence -> command.pdfId; else -> error("Unsupported study control")
        }
        val pdf = pdfs.firstOrNull { it.id == pdfId } ?: error("PDF unavailable")
        require(state.navigation.pdfId == pdfId && state.navigation.destination == AppDestination.STUDY) { "Open this PDF's study session first" }
        if (command is AppCommand.AnalyzeStudy) return state to listOf(AppEffect.AnalyzeStudy(pdfId))
        if (command is AppCommand.CancelStudyWork) return state to listOf(AppEffect.CancelStudy(pdfId))
        val previous = state.studyPlans[pdfId]
        if (command is AppCommand.ShowStudyTopics && previous != null) return state.copy(studyPlans = state.studyPlans +
            (pdfId to previous.copy(mode = StudyMode.TOPICS, guided = false, version = previous.version + 1))) to emptyList()
        if (command is AppCommand.GuideStudy && !command.enabled && previous != null) {
            require(command.expectedVersion == previous.version)
            return state.copy(studyPlans = state.studyPlans + (pdfId to previous.copy(guided=false,version=previous.version+1))) to emptyList()
        }
        val outline = outlines.get(pdf.document.id) ?: error("Analyze the document first")
        DocumentStudyRules.validate(outline); require(outline.checksum == pdf.document.checksum)
        require(outline.indexedPages.size == outline.pageCount) { "Finish topic analysis first" }
        if (command is AppCommand.SelectStudyTopics) {
            require(command.expectedOutline == DocumentStudyRules.fingerprint(outline)) { "Topic map changed; refresh your selection" }
            require(command.topicIds.distinct().size == command.topicIds.size)
            val selected = outline.topics.filter { it.id in command.topicIds }; require(selected.size == command.topicIds.size)
            val pages = selected.flatMap { (it.firstPage..it.lastPage).toList() }.distinct().sorted()
            val plan = DocumentStudyPlan(pdfId, pdf.document.checksum, command.expectedOutline, command.topicIds, pages,
                version = (previous?.version ?: 0) + 1, pageIndex = pages.firstOrNull() ?: 0,
                attachedSources = StudyComposition.normalizePrimary(pdfId,pages,previous?.attachedSources.orEmpty()))
            return state.copy(studyPlans = state.studyPlans + (pdfId to plan)) to emptyList()
        }
        val plan = previous ?: error("Choose at least one topic first")
        val sources = StudySources.resolve(plan,pdfs,outlines)
        return when (command) {
            is AppCommand.BeginStudy -> {
                require(command.expectedVersion == plan.version)
                require(plan.pages.isNotEmpty()) { "Choose at least one topic first" }
                val updated = plan.copy(mode = StudyMode.READING, guided = false, version = plan.version + 1, preparationVersion = plan.version + 1)
                val active = StudySources.active(plan,sources)
                val opened = focus.open(state, active.pdf.document.id, plan.pageIndex, null, policy)
                opened.copy(studyPlans = opened.studyPlans + (pdfId to updated)) to listOf(AppEffect.PrepareStudy(pdfId, updated.preparationVersion))
            }
            is AppCommand.ShowStudyTopics -> state.copy(studyPlans = state.studyPlans + (pdfId to plan.copy(
                mode = StudyMode.TOPICS, guided = false, version = plan.version + 1))) to emptyList()
            else -> guide.apply(state, command, plan, sources, policy) to emptyList()
        }
    }
}
