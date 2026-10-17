package dev.folio.runtime.command

import dev.folio.model.command.ExecutionPolicy
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.registry.ComponentRegistry

data class HandlerContext(val catalog: SourceCatalog, val registry: ComponentRegistry, val policy: ExecutionPolicy)
interface CommandHandler {
    fun supports(command: WorkspaceCommand): Boolean
    fun apply(state: Workspace, command: WorkspaceCommand, context: HandlerContext): Workspace
}
