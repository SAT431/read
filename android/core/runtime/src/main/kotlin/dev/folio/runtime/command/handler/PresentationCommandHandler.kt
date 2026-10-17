package dev.folio.runtime.command.handler

import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.*
import dev.folio.runtime.validation.CommandGuards

class PresentationCommandHandler : CommandHandler {
    override fun supports(command: WorkspaceCommand) = command is WorkspaceCommand.HideComponent ||
        command is WorkspaceCommand.SetPresentation || command is WorkspaceCommand.SetPriority ||
        command is WorkspaceCommand.PlayTransition

    override fun apply(state: Workspace, command: WorkspaceCommand, context: HandlerContext): Workspace {
        val id = when (command) {
            is WorkspaceCommand.HideComponent -> command.componentId
            is WorkspaceCommand.SetPresentation -> command.componentId
            is WorkspaceCommand.SetPriority -> command.componentId
            is WorkspaceCommand.PlayTransition -> command.componentId
            else -> error("Unsupported presentation command")
        }
        val component = state.components[id] ?: error("Unknown component")
        CommandGuards.mayRearrange(component, context.policy)
        val contract = context.registry.require(component.kind)
        val updated = when (command) {
            is WorkspaceCommand.HideComponent -> component.copy(visible = false)
            is WorkspaceCommand.SetPresentation -> {
                require(command.presentation in contract.presentations) { "Unsupported presentation" }
                component.copy(presentation = command.presentation)
            }
            is WorkspaceCommand.SetPriority -> {
                require(command.priority in 0..10); component.copy(priority = command.priority)
            }
            is WorkspaceCommand.PlayTransition -> {
                require(command.motion in contract.motions); component.copy(motion = command.motion)
            }
            else -> error("Unsupported command")
        }
        return state.copy(components = state.components + (id to updated))
    }
}
