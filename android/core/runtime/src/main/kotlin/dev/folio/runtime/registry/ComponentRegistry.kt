package dev.folio.runtime.registry

import dev.folio.model.ui.*

class ComponentRegistry(contracts: List<ComponentContract>) {
    private val entries = contracts.associateBy { it.kind }
    init {
        require(entries.size == contracts.size) { "Duplicate component registrations" }
        contracts.forEach { require(it.minimumWidthDp > 0 && it.preferredWidthDp >= it.minimumWidthDp) }
    }
    fun require(kind: ComponentKind): ComponentContract = entries[kind] ?: error("Unregistered component: $kind")
    fun all(): List<ComponentContract> = entries.values.toList()
}
