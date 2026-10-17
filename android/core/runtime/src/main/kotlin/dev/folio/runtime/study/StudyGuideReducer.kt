package dev.folio.runtime.study

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.*
import dev.folio.runtime.reading.*

internal class StudyGuideReducer(private val catalog: SourceCatalog, executor: CommandExecutor) {
    private val focus = StudyTargetFocus(executor, catalog)
    fun apply(state: Workspace, command: AppCommand, plan: DocumentStudyPlan, sources: List<StudySource>, policy: ExecutionPolicy): Workspace {
        require(plan.mode == StudyMode.READING) { "Open your reading collection first" }
        if(command is AppCommand.GuideStudy || command is AppCommand.FinishStudyLine)
            require(StudySources.ready(sources,catalog)) { "Prepare your selected pages first" }
        val targets = StudySources.sentences(sources,catalog)
        val activityId = "reader-${StudySources.active(plan,sources).pdf.document.id}"
        val reader = state.activities[activityId]?.data as? ActivityData.Reader ?: error("Study reader unavailable")
        fun sourceId(documentId: String) = sources.first { it.pdf.document.id == documentId }.pdf.id
        fun confirmed(sentence: dev.folio.model.source.SourceSentence) =
            (state.activities["reader-${sentence.source.documentId}"]?.data as? ActivityData.Reader)?.readingConfirmations?.values.orEmpty().any {
                it.sentenceId == sentence.id && ReadingConfirmations.isCurrent(it,catalog)
            }
        var next = state
        val updated = when (command) {
            is AppCommand.GuideStudy -> {
                require(command.expectedVersion == plan.version)
                val target = targets.firstOrNull { it.id == plan.sentenceId } ?: targets.firstOrNull { sentence ->
                    sentence.source.documentId == StudySources.active(plan,sources).pdf.document.id &&
                        sentence.source.pageIndex == plan.pageIndex && !confirmed(sentence)
                } ?: targets.firstOrNull { sentence ->
                    !confirmed(sentence)
                }
                if (command.enabled) {
                    require(target != null) { "All selected lines are already reported read, or this selection has no readable lines" }
                    next = focus.open(next, target.source.documentId, target.source.pageIndex, target, policy)
                }
                plan.copy(guided = command.enabled, sentenceId = target?.id, pageIndex = target?.source?.pageIndex ?: plan.pageIndex,
                    activeSourcePdfId = target?.source?.documentId?.let(::sourceId) ?: plan.activeSourcePdfId)
            }
            is AppCommand.FinishStudyLine -> {
                require(command.expectedVersion == plan.version && plan.guided && command.sentenceId == plan.sentenceId) { "The highlighted line changed. Confirm the current line." }
                val index = targets.indexOfFirst { it.id == command.sentenceId }; require(index >= 0) { "Source wording changed; restart the guide" }
                val target = targets[index]
                require(state.context.source == target.source && reader.selectedSentence == target.id) { "Reading focus changed. Restart guidance before confirming." }
                next = LearningReducer(catalog).reduce(next, LearningInteraction.ConfirmReading(activityId, target.source, target.id, reader.readingRevision), policy.actor)
                val following = targets.getOrNull(index + 1)
                if (following != null) next = focus.open(next, following.source.documentId, following.source.pageIndex, following, policy)
                plan.copy(guided = following != null, sentenceId = following?.id, pageIndex = following?.source?.pageIndex ?: plan.pageIndex,
                    activeSourcePdfId = following?.source?.documentId?.let(::sourceId) ?: plan.activeSourcePdfId)
            }
            is AppCommand.StudyPage -> {
                val source = sources.firstOrNull { it.pdf.id == (command.sourcePdfId ?: plan.activeSourcePdfId ?: plan.pdfId) } ?: error("Source is not in this collection")
                require(command.expectedVersion == plan.version && command.pageIndex in source.plan.pages)
                require(catalog.page(source.pdf.document.id,command.pageIndex)!=null) { "This source page is still preparing" }
                val first = targets.firstOrNull { it.source.documentId == source.pdf.document.id && it.source.pageIndex == command.pageIndex }
                next = focus.open(next, source.pdf.document.id, command.pageIndex, if (plan.guided) first else null, policy)
                plan.copy(pageIndex = command.pageIndex, activeSourcePdfId=source.pdf.id, sentenceId = if (plan.guided) first?.id else null, guided = plan.guided && first != null)
            }
            is AppCommand.StudySentence -> {
                require(command.expectedVersion == plan.version)
                val target = targets.firstOrNull { it.id == command.sentenceId } ?: error("Selected line is unavailable")
                next = focus.open(next, target.source.documentId, target.source.pageIndex, target, policy)
                plan.copy(pageIndex = target.source.pageIndex, sentenceId = target.id, activeSourcePdfId=sourceId(target.source.documentId))
            }
            else -> error("Unsupported study guide control")
        }
        return next.copy(studyPlans = next.studyPlans + (plan.pdfId to updated.copy(version = plan.version + 1)))
    }
}
