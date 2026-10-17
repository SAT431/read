package dev.folio.runtime.state

import dev.folio.model.workspace.Workspace

class LayoutUndo(private val capacity: Int = 20) {
    private val history = ArrayDeque<Workspace>()
    fun record(state: Workspace) { history.addLast(state); while (history.size > capacity) history.removeFirst() }
    fun restore(current: Workspace, protectPins: Boolean = false): Workspace? {
        val prior = history.removeLastOrNull() ?: return null
        // Restore layout only. Answers, notes, activity data and navigation context stay current.
        return current.copy(revision = current.revision + 1, focusedComponentId = prior.focusedComponentId,
            components = current.components.mapValues { (id, instance) ->
                if (protectPins && instance.pinned) return@mapValues instance
                val old = prior.components[id]
                if (old == null) instance.copy(visible = false) else instance.copy(visible = old.visible,
                    priority = old.priority, presentation = old.presentation, motion = old.motion, pinned = old.pinned)
            })
    }
}
