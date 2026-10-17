package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.activity.ActivityStatus
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.*
import dev.folio.runtime.state.LayoutUndo
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LearningStateTest {
    private val reducer = LearningReducer()
    @Test fun pageNavigationRestoresEachPagesReadingPosition() {
        val initial = reducer.reduce(TestFixture.reader(), LearningInteraction.SetReadingPosition("read", 2, 90))
        val second = TestFixture.execute(initial, WorkspaceCommand.OpenPage("doc", 1)).state
        val secondScrolled = reducer.reduce(second, LearningInteraction.SetReadingPosition("read", 5, 20))
        val returned = TestFixture.execute(secondScrolled, WorkspaceCommand.OpenPage("doc", 0)).state
        val reader = returned.activities.getValue("read").data as ActivityData.Reader
        assertEquals(2, reader.scrollIndex); assertEquals(90, reader.scrollOffset)
    }
    @Test fun answerSurvivesSuspendSourceReviewResumeAndSnapshot() {
        var state = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        state = reducer.reduce(state, LearningInteraction.SelectAnswer("quiz", "q1", 2))
        state = TestFixture.execute(state, WorkspaceCommand.SuspendActivity("quiz"), WorkspaceCommand.FocusSource(TestFixture.source)).state
        state = Json.decodeFromString<Workspace>(Json.encodeToString(state))
        state = TestFixture.execute(state, WorkspaceCommand.ResumeActivity("quiz")).state
        assertEquals(2, (state.activities.getValue("quiz").data as ActivityData.Quiz).answers["q1"])
        assertEquals(ActivityStatus.ACTIVE, state.activities.getValue("quiz").status)
    }
    @Test fun submissionLocksAnswerAndRequiresSelection() {
        val initial = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        assertTrue(runCatching { reducer.reduce(initial, LearningInteraction.SubmitAnswer("quiz", "q1")) }.isFailure)
        val selected = reducer.reduce(initial, LearningInteraction.SelectAnswer("quiz", "q1", 0))
        val submitted = reducer.reduce(selected, LearningInteraction.SubmitAnswer("quiz", "q1"))
        assertTrue(runCatching { reducer.reduce(submitted, LearningInteraction.SelectAnswer("quiz", "q1", 1)) }.isFailure)
    }
    @Test fun undoCannotRollBackLearnerAnswers() {
        val original = TestFixture.reader()
        val undo = LayoutUndo(); undo.record(original)
        val created = TestFixture.execute(original, TestFixture.quiz()).state
        val answered = reducer.reduce(created, LearningInteraction.SelectAnswer("quiz", "q1", 1))
        val restored = undo.restore(answered)!!
        assertEquals(1, (restored.activities.getValue("quiz").data as ActivityData.Quiz).answers["q1"])
        assertFalse(restored.components.getValue("quiz-view").visible)
    }
}
