package dev.folio.runtime.command

import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.handler.*
import dev.folio.runtime.registry.ComponentRegistry

/** Pure transaction: only its final immutable state can be committed by the controller. */
class CommandExecutor(private val registry: ComponentRegistry, private val catalog: SourceCatalog,
    private val handlers: List<CommandHandler> = listOf(ActivityCommandHandler(), PresentationCommandHandler(), NavigationCommandHandler())) {
    fun execute(state: Workspace, batch: CommandBatch, policy: ExecutionPolicy): ExecutionResult {
        val names = batch.commands.map { it.javaClass.simpleName }
        return try {
            require(batch.schemaVersion == 1 && batch.commands.size in 1..24) { "Invalid command batch" }
            require(batch.expectedRevision == null || batch.expectedRevision == state.revision) { "Stale workspace revision" }
            require(policy.actor != CommandActor.AI || !policy.interactionLocked || policy.userRequestedTransition) {
                "AI commands deferred during active interaction"
            }
            val context = HandlerContext(catalog, registry, policy)
            val result = batch.commands.fold(state) { current, command ->
                val handler = handlers.singleOrNull { it.supports(command) } ?: error("Missing or ambiguous command handler")
                handler.apply(current, command, context)
            }.copy(revision = state.revision + 1)
            ExecutionResult(result, CommandTrace(batch.requestId, true, names))
        } catch (error: IllegalArgumentException) {
            rejected(state, batch.requestId, names, error)
        } catch (error: IllegalStateException) {
            rejected(state, batch.requestId, names, error)
        }
    }
    private fun rejected(state: Workspace, id: String, names: List<String>, error: Exception) =
        ExecutionResult(state, CommandTrace(id, false, names, error.message ?: "Invalid command"))
}
