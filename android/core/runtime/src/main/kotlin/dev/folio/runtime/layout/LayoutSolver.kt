package dev.folio.runtime.layout

import dev.folio.model.activity.ActivityStatus
import dev.folio.model.ui.ComponentKind
import dev.folio.model.ui.Presentation
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.registry.ComponentRegistry

/** Composes registered instances, not named screens. Inline children follow source readers. */
class LayoutSolver(private val registry: ComponentRegistry) {
    fun solve(state: Workspace, availableWidthDp: Float, gapDp: Float = 16f): LayoutPlan {
        require(availableWidthDp.isFinite() && availableWidthDp > 0 && gapDp >= 0)
        val active = state.components.values.filter { it.visible && state.activities[it.activityId]?.status == ActivityStatus.ACTIVE }
        val capsules = active.filter { it.presentation == Presentation.CAPSULE }
        val surfaces = active.filter { it.presentation != Presentation.CAPSULE }
        val children = mutableMapOf<String, MutableList<dev.folio.model.ui.ComponentInstance>>()
        val roots = surfaces.filter { child ->
            if (child.presentation != Presentation.INLINE) true else {
                val source = state.activities.getValue(child.activityId).source
                val reader = surfaces.firstOrNull { candidate ->
                    val other = state.activities.getValue(candidate.activityId).source
                    candidate.kind == ComponentKind.READER && source.documentId == other.documentId && source.pageIndex == other.pageIndex
                }
                if (reader == null) true else { children.getOrPut(reader.id) { mutableListOf() }.add(child); false }
            }
        }
        val ordered = roots.sortedWith(compareByDescending<dev.folio.model.ui.ComponentInstance> {
            it.pinned || children[it.id].orEmpty().any { c -> c.pinned }
        }.thenByDescending { it.id == state.focusedComponentId || children[it.id].orEmpty().any { c -> c.id == state.focusedComponentId } }
            .thenByDescending { it.priority })
        val groups = ordered.map { root ->
            val members = listOf(root) + children[root.id].orEmpty()
            LayoutGroup(members, members.maxOf { registry.require(it.kind).minimumWidthDp },
                members.maxOf { maxOf(1, it.priority) }.toFloat() * if (members.any { it.id == state.focusedComponentId }) 1.4f else 1f)
        }
        if (groups.isEmpty()) return LayoutPlan(emptyList(), capsules, false)
        val bins = mutableListOf<MutableList<LayoutGroup>>()
        for (group in groups) {
            val occupied = bins.sumOf { bin -> bin.maxOf { it.minimumWidthDp } } + gapDp * bins.size
            if (bins.isEmpty() || occupied + group.minimumWidthDp <= availableWidthDp) bins.add(mutableListOf(group))
            else bins.last().add(group)
        }
        // A dominant root owns a full-width stack; all other activities remain preserved.
        if (groups.any { it.components.any { c -> c.presentation == Presentation.DOMINANT } }) {
            return LayoutPlan(listOf(LayoutColumn(availableWidthDp, groups.sortedByDescending {
                it.components.any { c -> c.presentation == Presentation.DOMINANT }
            })), capsules, availableWidthDp < groups.maxOf { it.minimumWidthDp })
        }
        val minima = bins.map { it.maxOf { g -> g.minimumWidthDp }.toFloat().coerceAtMost(availableWidthDp) }
        val weights = bins.map { it.maxOf { g -> g.weight } }
        val surplus = (availableWidthDp - minima.sum() - gapDp * (bins.size - 1)).coerceAtLeast(0f)
        val columns = bins.mapIndexed { index, bin ->
            LayoutColumn(minima[index] + surplus * weights[index] / weights.sum(), bin.toList())
        }
        return LayoutPlan(columns, capsules, availableWidthDp < groups.maxOf { it.minimumWidthDp })
    }
}
