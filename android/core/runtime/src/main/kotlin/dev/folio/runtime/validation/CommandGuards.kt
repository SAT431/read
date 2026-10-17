package dev.folio.runtime.validation

import dev.folio.model.command.CommandActor
import dev.folio.model.command.ExecutionPolicy
import dev.folio.model.ui.ComponentInstance

object CommandGuards {
    fun id(value: String) { require(value.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid identifier" } }
    fun mayRearrange(component: ComponentInstance, policy: ExecutionPolicy) {
        require(policy.actor != CommandActor.AI || !component.pinned) { "Pinned component protected" }
        require(policy.actor != CommandActor.AI || !policy.interactionLocked || policy.userRequestedTransition) {
            "Layout deferred during active interaction"
        }
    }
}
