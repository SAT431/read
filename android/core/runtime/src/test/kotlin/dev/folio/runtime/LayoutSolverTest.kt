package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.*
import dev.folio.runtime.layout.LayoutSolver
import org.junit.Assert.*
import org.junit.Test

class LayoutSolverTest {
    private val solver = LayoutSolver(TestFixture.registry)
    private fun composition() = TestFixture.execute(TestFixture.reader(), TestFixture.quiz(),
        WorkspaceCommand.CreateActivity("explain", "explain-view", ComponentKind.EXPLANATION, TestFixture.source, ActivityData.Explanation("Explanation"))).state
    @Test fun sameComponentsRecomposeWithoutOverlapAtDifferentWidths() {
        val state = composition()
        for (width in listOf(240f, 360f, 800f, 1300f)) {
            val plan = solver.solve(state, width)
            assertEquals(3, plan.columns.sumOf { it.groups.sumOf { group -> group.components.size } })
            assertTrue(plan.columns.sumOf { it.widthDp.toDouble() } + 16 * (plan.columns.size - 1) <= width + .01)
            assertTrue(plan.columns.all { it.widthDp > 0 })
        }
        assertEquals(1, solver.solve(state, 360f).columns.size)
        assertEquals(3, solver.solve(state, 1300f).columns.size)
    }
    @Test fun inlineEnrichmentGroupsWithSourceReader() {
        val state = TestFixture.execute(composition(), WorkspaceCommand.SetPresentation("explain-view", Presentation.INLINE)).state
        val groups = solver.solve(state, 1000f).columns.flatMap { it.groups }
        val sourceGroup = groups.first { it.components.any { component -> component.id == "read-view" } }
        assertTrue(sourceGroup.components.any { it.id == "explain-view" })
    }
    @Test fun capsulesDoNotConsumeSurfaceColumnsAndPinsTakePriority() {
        val state = composition().let { s -> s.copy(components = s.components.mapValues { (id, c) ->
            when (id) { "quiz-view" -> c.copy(presentation = Presentation.CAPSULE); "read-view" -> c.copy(pinned = true); else -> c }
        }) }
        val plan = solver.solve(state, 1000f)
        assertEquals("quiz-view", plan.capsules.single().id)
        assertEquals("read-view", plan.columns.first().groups.first().components.first().id)
    }
}
