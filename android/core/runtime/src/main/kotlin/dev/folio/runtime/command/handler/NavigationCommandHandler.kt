package dev.folio.runtime.command.handler

import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.*
import dev.folio.runtime.navigation.SourceNavigator

class NavigationCommandHandler : CommandHandler {
    override fun supports(command: WorkspaceCommand) = command is WorkspaceCommand.FocusSource ||
        command is WorkspaceCommand.OpenChapter || command is WorkspaceCommand.OpenPage ||
        command is WorkspaceCommand.NextPage || command is WorkspaceCommand.PreviousPage || command is WorkspaceCommand.ShowOriginal

    override fun apply(state: Workspace, command: WorkspaceCommand, context: HandlerContext): Workspace {
        val navigator = SourceNavigator(context.catalog)
        return when (command) {
            is WorkspaceCommand.FocusSource -> navigator.open(state, command.source)
            is WorkspaceCommand.OpenPage -> navigator.open(state, SourceAnchor(command.documentId, command.pageIndex))
            is WorkspaceCommand.OpenChapter -> {
                val chapter = context.catalog.chapters().firstOrNull { it.id == command.chapterId } ?: error("Unknown chapter")
                val checkpoint = state.context.chapterCheckpoints[chapter.id]?.takeIf {
                    it.documentId == chapter.documentId && it.pageIndex in chapter.firstPage..chapter.lastPage && context.catalog.contains(it)
                }
                navigator.open(state, checkpoint ?: SourceAnchor(chapter.documentId, chapter.firstPage), chapter.id)
            }
            WorkspaceCommand.NextPage -> navigator.step(state, 1)
            WorkspaceCommand.PreviousPage -> navigator.step(state, -1)
            is WorkspaceCommand.ShowOriginal -> {
                require(state.context.source != null) { "No active PDF" }
                state.copy(context = state.context.copy(originalPdfVisible = command.visible))
            }
            else -> error("Unsupported navigation command")
        }
    }
}
