package dev.folio.runtime.practice

import dev.folio.model.command.ExecutionPolicy
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor

class RevisionNavigation(executor: CommandExecutor, catalog: SourceCatalog, curriculum: CurriculumCatalog) {
    private val navigation = StudyTargetNavigation(executor, catalog, curriculum)
    fun open(state: Workspace, id: String, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Workspace {
        val item = state.revisionItems[id] ?: error("Unknown revision item")
        return navigation.open(state, SavedStudyTarget(item.activityId, item.questionId, item.source, item.prompt), pdfs, policy)
    }
}
