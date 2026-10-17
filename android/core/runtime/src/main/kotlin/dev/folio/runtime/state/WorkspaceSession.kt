package dev.folio.runtime.state

import dev.folio.model.command.*
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.validation.WorkspaceValidator
import dev.folio.runtime.action.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One serialized commit boundary shared by local interactions and future AI responses. */
class WorkspaceSession(initial: Workspace, private val executor: CommandExecutor,
    private val repository: WorkspaceRepository, private val validator: WorkspaceValidator,
    private val reducer: LearningReducer = LearningReducer(), private val appExecutor: AppCommandExecutor? = null,
    timerClock: () -> Long = { System.nanoTime() / 1_000_000 }, initialForeground: Boolean = true) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(initial)
    private val mutableTraces = MutableStateFlow<List<CommandTrace>>(emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableUnavailable = MutableStateFlow<List<dev.folio.model.source.SourceAnchor>>(emptyList())
    private val undo = LayoutUndo()
    private var writer: SnapshotWriter? = null
    private var canSave = true
    private var foreground = initialForeground
    private val timers = dev.folio.runtime.practice.PracticeTimerCoordinator(timerClock)
    val state = mutableState.asStateFlow()
    val traces = mutableTraces.asStateFlow()
    val storageError = mutableError.asStateFlow()
    val recoveredUnavailableSources = mutableUnavailable.asStateFlow()

    fun attachWriter(scope: kotlinx.coroutines.CoroutineScope) {
        if (writer == null) writer = SnapshotWriter(scope, repository) { mutableError.value = it }
    }
    suspend fun flush() = lock.withLock { checkpointTimers(); if (canSave) writer?.flush(mutableState.value) }
    suspend fun tickTimers() = lock.withLock { checkpointTimers() }
    suspend fun setForeground(active: Boolean) = lock.withLock {
        checkpointTimers(); foreground = active; checkpointTimers()
        if (!active && canSave) writer?.flush(mutableState.value)
    }
    private suspend fun checkpointTimers() {
        val before = mutableState.value
        val next = timers.checkpoint(before, foreground)
        if (next != before) commit(next, critical = next.revision != before.revision)
    }

    suspend fun restore() = lock.withLock {
        try { repository.load()?.let {
            validator.validate(it, requireAvailableSources = false)
            val unavailable = validator.unavailableSources(it)
            val recovered = timers.recover(it); mutableState.value = recovered; mutableUnavailable.value = unavailable
            if (recovered != it) commit(recovered, critical = true)
        } }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { canSave = false; mutableError.value = "Saved workspace could not be loaded; original file preserved: ${error.message}" }
    }
    suspend fun dispatch(batch: CommandBatch, policy: ExecutionPolicy): ExecutionResult = lock.withLock {
        checkpointTimers()
        val result = executor.execute(mutableState.value, batch, policy)
        mutableTraces.value = (listOf(result.trace) + mutableTraces.value).take(50)
        if (result.trace.accepted) { undo.record(mutableState.value); commit(timers.checkpoint(result.state, foreground), critical = true) }
        result.copy(state = mutableState.value)
    }
    suspend fun dispatchApp(batch: AppCommandBatch, policy: ExecutionPolicy,
        contextCurrent: () -> Boolean = { true }): AppExecutionResult = lock.withLock {
        checkpointTimers()
        val result = if (!foreground && batch.commands.any { it is AppCommand.StartPracticeTimer }) {
            AppExecutionResult(mutableState.value, CommandTrace(batch.requestId, false,
                batch.commands.map(AppActionRegistry::id), "Return to Folio before starting a timer"))
        } else if (!contextCurrent()) {
            AppExecutionResult(mutableState.value, CommandTrace(batch.requestId, false,
                batch.commands.map(AppActionRegistry::id), "App context changed while waiting"))
        } else if (batch.commands == listOf(AppCommand.UndoLayout)) {
            val valid = batch.schemaVersion == 1 && (batch.expectedRevision == null || batch.expectedRevision == mutableState.value.revision) &&
                (policy.actor != CommandActor.AI || !policy.interactionLocked || policy.userRequestedTransition)
            val restored = if (valid) undo.restore(mutableState.value, protectPins = policy.actor == CommandActor.AI) else null
            AppExecutionResult(restored ?: mutableState.value, CommandTrace(batch.requestId, restored != null,
                listOf("workspace.undo_layout"), if (!valid) "Stale or protected app context" else if (restored == null) "No layout change to undo" else null))
        } else (appExecutor ?: error("App action runtime unavailable")).execute(mutableState.value, batch, policy)
        mutableTraces.value = (listOf(result.trace) + mutableTraces.value).take(50)
        if (result.trace.accepted) {
            if (batch.commands.any { it is AppCommand.WorkspaceAction }) undo.record(mutableState.value)
            val critical = batch.commands.any { (it !is AppCommand.SaveCorrectionDraft || it.draft == null) &&
                it !is AppCommand.RecallResponse && it !is AppCommand.SaveAnnotationDraft && it !is AppCommand.ReadingPosition && it !is AppCommand.Transform &&
                it !is AppCommand.PdfTransform && it !is AppCommand.SearchChapters && it !is AppCommand.SearchStudy }
            commit(timers.checkpoint(result.state, foreground), critical)
        }
        result.copy(state = mutableState.value)
    }
    suspend fun interact(event: LearningInteraction) = lock.withLock {
        checkpointTimers(); commit(timers.checkpoint(reducer.reduce(mutableState.value, event), foreground),
            critical = event is LearningInteraction.SelectAnswer || event is LearningInteraction.SubmitAnswer)
    }
    suspend fun undoLayout() = lock.withLock {
        checkpointTimers(); undo.restore(mutableState.value)?.let { commit(timers.checkpoint(it, foreground), critical = true) }
    }
    private suspend fun commit(next: Workspace, critical: Boolean = false) {
        mutableState.value = next
        if (canSave) { if (critical) writer?.flush(next) else writer?.enqueue(next) }
    }
}
