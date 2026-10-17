package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.*
import dev.folio.runtime.command.CommandCodec
import org.junit.Assert.*
import org.junit.Test

class CommandExecutorTest {
    @Test fun invalidSecondCommandRejectsEntireTransaction() {
        val original = TestFixture.reader()
        val result = TestFixture.execute(original, WorkspaceCommand.HideComponent("read-view"),
            WorkspaceCommand.FocusSource(SourceAnchor("missing", 0)))
        assertFalse(result.trace.accepted); assertSame(original, result.state)
        assertTrue(original.components.getValue("read-view").visible)
    }
    @Test fun pinsProtectAgainstAiButAllowUserControl() {
        val original = TestFixture.reader().let { it.copy(components = it.components.mapValues { (_, c) -> c.copy(pinned = true) }) }
        assertFalse(TestFixture.execute(original, WorkspaceCommand.HideComponent("read-view"), policy = TestFixture.ai).trace.accepted)
        assertTrue(TestFixture.execute(original, WorkspaceCommand.HideComponent("read-view")).trace.accepted)
    }
    @Test fun hidesPreserveActivityDataAndMultipleViewsCanShareOneActivity() {
        val state = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        val shared = TestFixture.execute(state, WorkspaceCommand.ShowComponent("second-view", "quiz", ComponentKind.QUIZ)).state
        val hidden = TestFixture.execute(shared, WorkspaceCommand.HideComponent("quiz-view")).state
        assertEquals(2, hidden.components.values.count { it.activityId == "quiz" })
        assertEquals(state.activities["quiz"], hidden.activities["quiz"])
    }
    @Test fun rejectsWrongKindRaggedTablesAndNonFigureDiagram() {
        val badKind = TestFixture.quiz().copy(kind = ComponentKind.READER)
        assertFalse(TestFixture.execute(TestFixture.reader(), badKind).trace.accepted)
        val table = WorkspaceCommand.CreateActivity("table", "table-view", ComponentKind.COMPARISON, TestFixture.source,
            ActivityData.Comparison(listOf("Header"), listOf(listOf("A", "B"))))
        assertFalse(TestFixture.execute(TestFixture.reader(), table).trace.accepted)
        val diagram = WorkspaceCommand.CreateActivity("diagram", "diagram-view", ComponentKind.DIAGRAM, TestFixture.source, ActivityData.Diagram())
        assertFalse(TestFixture.execute(TestFixture.reader(), diagram).trace.accepted)
    }
    @Test fun duplicateInstancesAreRejected() {
        assertFalse(TestFixture.execute(TestFixture.reader(), TestFixture.quiz(), TestFixture.quiz()).trace.accepted)
    }
    @Test fun staleResponsesAndAttentionLocksPreventExecution() {
        val original = TestFixture.reader()
        val batch = CommandBatch(requestId = "old", expectedRevision = 9, commands = listOf(TestFixture.quiz()))
        assertFalse(TestFixture.executor.execute(original, batch, TestFixture.ai).trace.accepted)
        val locked = ExecutionPolicy(CommandActor.AI, interactionLocked = true)
        assertFalse(TestFixture.execute(original, TestFixture.quiz(), policy = locked).trace.accepted)
        assertTrue(TestFixture.execute(original, TestFixture.quiz(), policy = locked.copy(userRequestedTransition = true)).trace.accepted)
    }
    @Test fun codecRoundTripsAndRejectsExecutableOrUnknownFields() {
        val codec = CommandCodec()
        val batch = CommandBatch(requestId = "sample", commands = listOf(TestFixture.quiz()))
        assertEquals(batch, codec.decode(codec.encode(batch)))
        for (payload in listOf("""{"requestId":"x","commands":[{"type":"RUN_CODE","javascript":"bad"}]}""",
            """{"requestId":"x","commands":[{"type":"NEXT_PAGE","css":"bad"}]}""")) {
            assertTrue(runCatching { codec.decode(payload) }.isFailure)
        }
    }
    @Test fun unsupportedPresentationsAndPrioritiesFailSafely() {
        assertFalse(TestFixture.execute(TestFixture.reader(), WorkspaceCommand.SetPresentation("read-view", Presentation.OVERLAY)).trace.accepted)
        assertFalse(TestFixture.execute(TestFixture.reader(), WorkspaceCommand.SetPriority("read-view", 99)).trace.accepted)
    }
}
