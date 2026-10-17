package dev.folio.runtime.interaction

import dev.folio.model.ui.ComponentAction

data class InteractionContract(val action: ComponentAction, val immediate: Boolean, val requiresActivity: Boolean)

object InteractionRegistry {
    val contracts = ComponentAction.entries.associateWith { InteractionContract(it, true, true) }
}
