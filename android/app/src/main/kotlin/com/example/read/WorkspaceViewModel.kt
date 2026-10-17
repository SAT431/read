package com.example.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.read.di.AppGraph
import com.example.read.fixture.ArchitectureActivities
import dev.folio.model.command.*
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.action.LearningCommandBridge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID

class WorkspaceViewModel(val graph: AppGraph) : ViewModel() {
    private val mutableLoading = MutableStateFlow(true)
    private val mutableError = MutableStateFlow<String?>(null)
    val loading = mutableLoading.asStateFlow()
    val error = mutableError.asStateFlow()
    private val effectChannel = Channel<AppEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    init {
        viewModelScope.launch {
            try {
                val warnings = dev.folio.runtime.state.WorkspaceStartupRecovery().recover(
                    graph.library::load, graph.pages::load, graph.session::restore)
                mutableError.value = warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
            }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = "Saved study work could not be restored. Existing stored data is preserved." }
            finally { mutableLoading.value = false }
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(1000); graph.session.tickTimers()
            }
        }
    }
    fun clearPageRecoveryWarning() {
        mutableError.value = mutableError.value?.lines()?.filterNot { it == dev.folio.runtime.state.WorkspaceStartupRecovery.PAGE_FAILURE }
            ?.joinToString("\n")?.takeIf { it.isNotBlank() }
    }
    fun flush() = viewModelScope.launch { graph.session.flush() }

    fun act(command: AppCommand) = appBatch(listOf(command))
    fun appBatch(commands: List<AppCommand>, policy: ExecutionPolicy = ExecutionPolicy(CommandActor.USER)) = viewModelScope.launch {
        val result = graph.session.dispatchApp(AppCommandBatch(requestId = UUID.randomUUID().toString(), commands = commands), policy)
        mutableError.value = result.trace.error
        result.effects.forEach { effectChannel.send(it) }
    }
    fun dispatch(commands: List<WorkspaceCommand>) = appBatch(commands.map(AppCommand::WorkspaceAction))
    fun interact(event: LearningInteraction) = act(LearningCommandBridge.command(event))
    fun addFixture(kind: ComponentKind): kotlinx.coroutines.Job {
        val commands = mutableListOf<WorkspaceCommand>()
        if (kind != ComponentKind.READER && graph.session.state.value.activities.values.none { it.data is dev.folio.model.activity.ActivityData.Reader }) {
            commands.add(ArchitectureActivities.create(ComponentKind.READER))
        }
        commands.add(ArchitectureActivities.create(kind))
        return dispatch(commands)
    }
    fun undo() = act(AppCommand.UndoLayout)
}
