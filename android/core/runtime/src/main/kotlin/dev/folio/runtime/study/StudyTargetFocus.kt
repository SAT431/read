package dev.folio.runtime.study

import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.SourceSentence
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.ReaderSentenceFocus

internal class StudyTargetFocus(private val executor: CommandExecutor, private val catalog: SourceCatalog) {
    fun open(state: Workspace, documentId: String, page: Int, sentence: SourceSentence?, policy: ExecutionPolicy): Workspace {
        val readerId = "reader-$documentId"
        if (policy.actor == CommandActor.AI) require(state.components.values.filter { it.pinned }.all { c ->
            state.activities[c.activityId]?.source?.let { it.documentId == documentId && it.pageIndex == page } == true
        }) { "A pinned study view prevents moving to that line" }
        val focus = if (readerId in state.activities) WorkspaceCommand.FocusActivity(readerId) else WorkspaceCommand.CreateActivity(
            readerId,"$readerId-view",dev.folio.model.ui.ComponentKind.READER,dev.folio.model.source.SourceAnchor(documentId,page),dev.folio.model.activity.ActivityData.Reader())
        val result = executor.execute(state, CommandBatch(requestId = "study-focus", commands = listOf(
            focus, WorkspaceCommand.OpenPage(documentId, page))), policy)
        require(result.trace.accepted) { result.trace.error.orEmpty() }
        val focused = if (sentence == null) result.state else ReaderSentenceFocus(catalog).apply(result.state,
            LearningInteraction.FocusSentence(readerId, sentence.id))
        return focused.copy(navigation = focused.navigation.copy(destination = AppDestination.STUDY, pdfPageIndex = page))
    }
}
