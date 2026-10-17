package com.example.read

import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*

/** Isolated sample; never reads or overwrites the learner workspace. */
internal class PracticeTimerFixture(clock: () -> Long, repository: WorkspaceRepository = object : WorkspaceRepository {
    override suspend fun load(): Workspace? = null
    override suspend fun save(workspace: Workspace) {}
}) {
    val catalog = ArchitectureSources.catalog()
    val curriculum = Neet2026Catalog()
    val registry = BuiltInComponents.registry()
    val executor = CommandExecutor(registry, catalog)
    val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>())
        override suspend fun load() {}
    }
    private val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
    private val quiz = ActivityData.Quiz(listOf(Question("timer-q", "Labelled timer fixture question",
        listOf("A", "B", "C", "D"), 0, "Fixture reference", listOf(source))), answers = mapOf("timer-q" to 1))
    private val created = executor.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "timer-fixture", commands = listOf(
        WorkspaceCommand.CreateActivity("timer-quiz", "timer-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
    init { assertTrue(created.trace.error, created.trace.accepted) }
    val workspace = WorkspaceSession(created.state.copy(navigation = AppNavigation(AppDestination.WORKSPACE)), executor,
        repository, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog), timerClock = clock)
    fun data() = workspace.state.value.activities.getValue("timer-quiz").data as ActivityData.Quiz
    suspend fun act(command: AppCommand) {
        val result = workspace.dispatchApp(AppCommandBatch(requestId = "timer-touch", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
        assertTrue(result.trace.error, result.trace.accepted)
    }
}
