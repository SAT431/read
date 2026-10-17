package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.QuizProgress
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class QuizPracticeTest {
    private val questions = (0..2).map { index -> Question("q$index", "Question ${index + 1}",
        listOf("A", "B", "C", "D"), index, "Explanation ${index + 1}", listOf(SourceAnchor("doc", index, "p1"))) }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://test.example"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    private fun initial(): Workspace = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.CreateActivity("quiz", "quiz-view",
        ComponentKind.QUIZ, questions.first().sources.first(), ActivityData.Quiz(questions))).state
    private fun apply(state: Workspace, vararg commands: AppCommand) = executor.execute(state,
        AppCommandBatch(requestId = "practice", expectedRevision = state.revision, commands = commands.toList()), TestFixture.user)
    private fun quiz(state: Workspace) = state.activities.getValue("quiz").data as ActivityData.Quiz

    @Test fun movingQuestionsPreservesChoicesAndChecksWithoutMovingReader() {
        val original = initial()
        val answered = apply(original, AppCommand.SelectAnswer("quiz", "q0", 1), AppCommand.SubmitAnswer("quiz", "q0")).state
        val moved = apply(answered, AppCommand.QuizQuestion("quiz", "q1"))
        assertTrue(moved.trace.accepted)
        assertEquals(1, quiz(moved.state).index)
        assertEquals(quiz(answered).answers, quiz(moved.state).answers)
        assertEquals(quiz(answered).submitted, quiz(moved.state).submitted)
        assertEquals(questions[1].sources.first(), moved.state.context.source)
        assertEquals(original.activities.getValue("read"), moved.state.activities.getValue("read"))
        val back = apply(moved.state, AppCommand.QuizQuestion("quiz", "q0")).state
        assertEquals(1, quiz(back).answers["q0"])
        assertTrue("q0" in quiz(back).submitted)
    }
    @Test fun skippingRetainsUnsubmittedSelectionAndCannotCountAsCompletion() {
        val state = apply(initial(), AppCommand.SelectAnswer("quiz", "q0", 0), AppCommand.QuizQuestion("quiz", "q2")).state
        val progress = QuizProgress.from(quiz(state))
        assertEquals(0, progress.checked)
        assertEquals(0, progress.correct)
        assertEquals(listOf(0, 1, 2), progress.unchecked)
        assertTrue(progress.mistakes.isEmpty())
    }
    @Test fun checkedAnswersCannotChangeOrBeSubmittedTwice() {
        val state = apply(initial(), AppCommand.SelectAnswer("quiz", "q0", 0), AppCommand.SubmitAnswer("quiz", "q0")).state
        for (command in listOf(AppCommand.SelectAnswer("quiz", "q0", 2), AppCommand.SubmitAnswer("quiz", "q0"))) {
            val result = apply(state, command)
            assertFalse(result.trace.accepted)
            assertEquals(state, result.state)
        }
    }
    @Test fun invalidOrSuspendedQuestionNavigationRejectsWholeBatch() {
        val original = initial()
        val invalid = apply(original, AppCommand.SelectAnswer("quiz", "q0", 0), AppCommand.QuizQuestion("quiz", "missing"))
        assertFalse(invalid.trace.accepted)
        assertEquals(original, invalid.state)
        val suspended = TestFixture.execute(original, WorkspaceCommand.SuspendActivity("quiz")).state
        assertFalse(apply(suspended, AppCommand.QuizQuestion("quiz", "q1")).trace.accepted)
        assertFalse(apply(original, AppCommand.QuizQuestion("read", "q1")).trace.accepted)
    }
    @Test fun summaryUsesOnlyCheckedAnswersAndCyclesReviewTargets() {
        val data = ActivityData.Quiz(questions, answers = mapOf("q0" to 0, "q1" to 0, "q2" to 2), submitted = setOf("q0", "q1"))
        val progress = QuizProgress.from(data)
        assertEquals(2, progress.checked)
        assertEquals(1, progress.correct)
        assertEquals(listOf(1), progress.mistakes)
        assertEquals(listOf(2), progress.unchecked)
        assertEquals(1, progress.nextAfter(2, progress.mistakes))
        assertNull(progress.nextAfter(0, emptyList()))
    }
    @Test fun aiCannotCreatePracticeWithFabricatedLearnerHistory() {
        for (data in listOf(ActivityData.Quiz(questions, answers = mapOf("q0" to 0)),
            ActivityData.Quiz(questions, answers = mapOf("q0" to 0), submitted = setOf("q0")))) {
            val command = WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, TestFixture.source, data)
            assertFalse(TestFixture.execute(TestFixture.reader(), command, policy = TestFixture.ai).trace.accepted)
        }
    }
    @Test fun navigationWireContractRoundTrips() {
        val batch = AppCommandBatch(requestId = "next", commands = listOf(AppCommand.QuizQuestion("quiz", "q2")))
        val codec = AppCommandCodec()
        assertEquals(batch, codec.decode(codec.encode(batch)))
    }
    @Test fun resumingAfterSourceReviewRestoresQuestionContextWithoutMovingReader() {
        val original = initial()
        val reviewed = TestFixture.execute(original, WorkspaceCommand.SuspendActivity("quiz"),
            WorkspaceCommand.FocusSource(SourceAnchor("doc", 2, "p1"))).state
        val resumed = TestFixture.execute(reviewed, WorkspaceCommand.ResumeActivity("quiz")).state
        assertEquals(questions.first().sources.first(), resumed.context.source)
        assertEquals("quiz", resumed.context.focusedActivityId)
        assertEquals(2, resumed.activities.getValue("read").source.pageIndex)
        assertEquals(quiz(original), quiz(resumed))
    }
}
