package dev.folio.runtime.practice

import dev.folio.model.command.ExecutionPolicy
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor

class LearningMemoryNavigation(executor: CommandExecutor, catalog: SourceCatalog, curriculum: CurriculumCatalog) {
    private val navigation = StudyTargetNavigation(executor, catalog, curriculum)
    fun open(state: Workspace, id: String, pdfs: List<LibraryPdf>, policy: ExecutionPolicy): Workspace {
        val observation = LearningMemory.observations(state).firstOrNull { it.id == id } ?: error("Saved observation changed or is unavailable")
        return navigation.open(state, SavedStudyTarget(observation.activityId, observation.questionId, observation.source, observation.prompt,
            observation.attemptNumber.takeIf { observation.questionId != null }), pdfs, policy)
    }
}
