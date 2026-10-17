package dev.folio.runtime.action

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.command.*
import dev.folio.runtime.interaction.LearningReducer

data class AppExecutionResult(val state: Workspace, val trace: CommandTrace, val effects: List<AppEffect> = emptyList())

class AppCommandExecutor(private val workspace: CommandExecutor, private val curriculum: CurriculumCatalog,
    private val library: PdfLibrary, private val sourceCatalog: SourceCatalog? = null, private val cacheRepair: SourceCacheRepairApproval? = null,
    studyOutlines: DocumentOutlineRepository? = null) {
    private val studyFlow = if (sourceCatalog != null && studyOutlines != null)
        dev.folio.runtime.study.DocumentStudyReducer(studyOutlines, sourceCatalog, workspace) else null
    private val navigation = LibraryNavigationReducer(curriculum)
    private val studyOpener = dev.folio.runtime.study.StudySessionOpener(workspace, sourceCatalog, curriculum)
    private val harness = if (studyOutlines != null && studyFlow != null) dev.folio.runtime.study.PdfHarnessReducer(studyOutlines,
        { state, pdf, policy -> studyOpener.open(state,pdf,library.entries.value,policy) },
        { state, pdf, page ->
            val opened = navigation.reduce(state,AppCommand.OpenPdf(pdf.id),library.entries.value)!!
            navigation.reduce(opened,AppCommand.PdfPage(page),library.entries.value)!!
        }, studyFlow) else null
    private val learning = LearningReducer(sourceCatalog)
    private val corrections = sourceCatalog?.let { dev.folio.runtime.engineering.SourceCorrectionReducer(it) }
    private val correctionDrafts = sourceCatalog?.let { dev.folio.runtime.engineering.SourceCorrectionDraftReducer(it) }
    private val revisions = sourceCatalog?.let { dev.folio.runtime.practice.RevisionQueueReducer(it) }
    private val revisionNavigation = sourceCatalog?.let { dev.folio.runtime.practice.RevisionNavigation(workspace, it, curriculum) }
    private val memoryNavigation = sourceCatalog?.let { dev.folio.runtime.practice.LearningMemoryNavigation(workspace, it, curriculum) }
    private val search = sourceCatalog?.let { dev.folio.runtime.search.SearchResultNavigation(workspace, curriculum, it) }
    private val savedNotes = sourceCatalog?.let { dev.folio.runtime.reading.SavedNotesReducer(it) }
    private val sourceReview = sourceCatalog?.let { SourceReviewNavigation(it, navigation) }
    fun execute(state: Workspace, batch: AppCommandBatch, policy: ExecutionPolicy): AppExecutionResult {
        val names = batch.commands.map(AppActionRegistry::id)
        return try {
            require(batch.schemaVersion == 1 && batch.commands.size in 1..24)
            require(batch.expectedRevision == null || batch.expectedRevision == state.revision) { "Stale app context" }
            require(policy.actor != CommandActor.AI || !policy.interactionLocked || policy.userRequestedTransition)
            var next = state
            val effects = mutableListOf<AppEffect>()
            for (command in batch.commands) {
                require(AppActionRegistry.all.first { it.id == AppActionRegistry.id(command) }.enabled) { "Capability not connected yet" }
                when (command) {
                    is AppCommand.HarnessAnalyzePdf, is AppCommand.HarnessBrowseTopics, is AppCommand.HarnessOpenTopic, is AppCommand.HarnessInspectTopic, is AppCommand.HarnessInspectPage,
                    is AppCommand.HarnessIncludeTopics, is AppCommand.HarnessRemoveSource -> {
                        val result = (harness ?: error("PDF harness unavailable")).apply(next,command,library.entries.value,policy)
                        next=result.first; effects.addAll(result.second)
                    }
                    is AppCommand.AnalyzeStudy, is AppCommand.CancelStudyWork, is AppCommand.SelectStudyTopics, is AppCommand.BeginStudy,
                    is AppCommand.ShowStudyTopics, is AppCommand.GuideStudy, is AppCommand.FinishStudyLine, is AppCommand.StudyPage, is AppCommand.StudySentence -> {
                        val result = (studyFlow ?: error("Document study unavailable")).apply(next, command, library.entries.value, policy)
                        next = result.first; effects.addAll(result.second)
                    }
                    is AppCommand.OpenSearchResult -> next = (search ?: error("Search unavailable")).open(next,
                        command.target, command.expectedFingerprint, library.entries.value, policy)
                    is AppCommand.OpenSavedAnnotation, is AppCommand.BeginSavedNoteEdit, is AppCommand.EditSavedDraft,
                    is AppCommand.CommitSavedDraft, is AppCommand.DiscardSavedDraft -> {
                        next = (savedNotes ?: error("Saved notes unavailable")).apply(next, command, policy.actor)
                        if (command is AppCommand.OpenSavedAnnotation) next.context.source?.let {
                            next = dev.folio.runtime.practice.StudyPdfContext.sync(next, it, library.entries.value, curriculum)
                        }
                    }
                    AppCommand.CheckSourceCache -> {
                        require(cacheRepair != null) { "Cache repair unavailable" }; effects.add(AppEffect.CheckSourceCache)
                    }
                    is AppCommand.ConfirmSourceCacheRepair -> {
                        require(policy.actor == CommandActor.USER) { "Only the learner can confirm cache repair" }
                        require(cacheRepair?.canConfirm(command.token) == true) { "Repair preview changed; check again" }
                        effects.add(AppEffect.ConfirmSourceCacheRepair(command.token))
                    }
                    AppCommand.DismissSourceCacheRepair -> {
                        require(cacheRepair != null) { "Cache repair unavailable" }; effects.add(AppEffect.DismissSourceCacheRepair)
                    }
                    is AppCommand.OpenLearningObservation -> next = (memoryNavigation ?: error("Learning source unavailable"))
                        .open(next, command.id, library.entries.value, policy)
                    is AppCommand.ScheduleRevision, is AppCommand.AcceptRevisionSuggestion, is AppCommand.CompleteRevision, is AppCommand.RemoveRevision ->
                        next = (revisions ?: error("Revision source unavailable")).apply(next, command, policy.actor)
                    is AppCommand.OpenRevision -> next = (revisionNavigation ?: error("Revision source unavailable"))
                        .open(next, command.id, library.entries.value, policy)
                    is AppCommand.ReviewRegion -> next = (sourceReview ?: error("Source comparison unavailable")).apply(next, command, library.entries.value)
                    is AppCommand.SaveCorrectionDraft -> next = (correctionDrafts ?: error("Correction source unavailable")).apply(next, command, policy.actor)
                    is AppCommand.ProposeCorrection, is AppCommand.ApproveCorrection, is AppCommand.RemoveCorrection ->
                        next = (corrections ?: error("Correction source unavailable")).apply(next, command, policy.actor)
                    AppCommand.StartVoice -> {
                        next = next.copy(preferences = next.preferences.copy(voiceAutomaticSend = true))
                        effects.add(AppEffect.StartVoice)
                    }
                    AppCommand.FinishVoice -> effects.add(AppEffect.FinishVoice)
                    AppCommand.CancelVoice -> effects.add(AppEffect.CancelVoice)
                    AppCommand.ReviewVoice -> {
                        next = navigation.reduce(next, AppCommand.Navigate(AppDestination.ASSISTANT), library.entries.value)!!
                        effects.add(AppEffect.ReviewVoice)
                    }
                    is AppCommand.VoiceAutoSend -> next = next.copy(preferences = next.preferences.copy(voiceAutomaticSend = command.enabled))
                    is AppCommand.EngineerPage -> {
                        val document = library.entries.value.firstOrNull { it.document.id == command.documentId }?.document ?: error("Unknown imported PDF")
                        require(document.localPdfPath != null && command.pageIndex in 0 until document.pageCount)
                        effects.add(AppEffect.EngineerPage(EngineeringRequest(batch.requestId, document.id, command.pageIndex, document.checksum, PageEngineeringVersion.CURRENT)))
                    }
                    is AppCommand.RequestPdfImport -> {
                        require(curriculum.chapters().any { it.id == command.chapterId })
                        effects.add(AppEffect.RequestPdfImport(command.chapterId))
                    }
                    is AppCommand.WorkspaceAction -> {
                        val result = workspace.execute(next, CommandBatch(requestId = batch.requestId, commands = listOf(command.command)), policy)
                        require(result.trace.accepted) { result.trace.error.orEmpty() }; next = result.state
                    }
                    is AppCommand.StudyPdf -> {
                        val saved=next.studyPlans[command.pdfId]
                        if(!command.resume && saved!=null) next=next.copy(studyPlans=next.studyPlans +
                            (command.pdfId to saved.copy(mode=dev.folio.model.study.StudyMode.TOPICS,guided=false,version=saved.version+1)))
                        next=study(next,library.entries.value.firstOrNull { it.id==command.pdfId } ?: error("Unknown PDF"),policy)
                    }
                    AppCommand.UndoLayout -> error("Layout undo is a standalone controller operation")
                    else -> {
                        val interaction = LearningCommandBridge.event(command)
                        if (interaction != null) {
                            require(policy.actor != CommandActor.AI || command !is AppCommand.Pin) { "Only the user can change pins" }
                            next = learning.reduce(next, interaction, policy.actor)
                            if (command is AppCommand.OpenReadingConfirmation || command is AppCommand.OpenReadingBookmark || command is AppCommand.ReviewQuizAttempt || command is AppCommand.RestartQuiz || command is AppCommand.StartPracticeTimer || command is AppCommand.QuizQuestion)
                                next.context.source?.let { source ->
                                    next = dev.folio.runtime.practice.StudyPdfContext.sync(next, source, library.entries.value, curriculum)
                                    if (command !is AppCommand.QuizQuestion) next = next.copy(navigation = next.navigation.copy(destination = AppDestination.WORKSPACE))
                                }
                        } else next = navigation.reduce(next, command, library.entries.value) ?: error("No semantic handler")
                    }
                }
            }
            AppExecutionResult(next.copy(revision = state.revision + 1), CommandTrace(batch.requestId, true, names), effects.map {
                if(it is AppEffect.AnalyzeStudy) it.copy(requestId=batch.requestId) else it
            })
        } catch (error: IllegalArgumentException) { AppExecutionResult(state, CommandTrace(batch.requestId, false, names, error.message)) }
        catch (error: IllegalStateException) { AppExecutionResult(state, CommandTrace(batch.requestId, false, names, error.message)) }
    }
    private fun study(state: Workspace, pdf: LibraryPdf, policy: ExecutionPolicy) = studyOpener.open(state,pdf,library.entries.value,policy)
}
