package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.QuizConfidenceReducer
import dev.folio.runtime.state.LayoutUndo
import dev.folio.runtime.validation.ActivityValidator
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class QuizConfidenceTest {
    private val reducer = QuizConfidenceReducer { 123L }
    private fun initial() = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
    private fun selected(option: Int = 1) = LearningReducer().reduce(initial(), LearningInteraction.SelectAnswer("quiz", "q1", option))
    private fun data(state: Workspace) = state.activities.getValue("quiz").data as ActivityData.Quiz
    private fun confidence(state: Workspace, level: ConfidenceLevel?, expected: ConfidenceLevel? = null, option: Int = 1,
        actor: CommandActor = CommandActor.USER) = reducer.apply(state, LearningInteraction.SetAnswerConfidence("quiz", "q1", option, expected, level), actor)
    @Test fun explicitConfidenceUsesNativeTimeAndActorWithoutChangingAnswerOrChecking() {
        val before = selected(); val after = confidence(before, ConfidenceLevel.HIGH, actor = CommandActor.AI)
        assertEquals(AnswerConfidence(ConfidenceLevel.HIGH, 1, 123L, true), data(after).confidence["q1"])
        assertEquals(data(before).answers, data(after).answers); assertTrue(data(after).submitted.isEmpty())
        assertEquals(before.context, after.context)
    }
    @Test fun anAnswerMustBeSelectedAndStaleOptionOrConfidenceIsRejected() {
        assertTrue(runCatching { confidence(initial(), ConfidenceLevel.LOW) }.isFailure)
        assertTrue(runCatching { confidence(selected(), ConfidenceLevel.LOW, option = 0) }.isFailure)
        val low = confidence(selected(), ConfidenceLevel.LOW)
        assertTrue(runCatching { confidence(low, ConfidenceLevel.HIGH) }.isFailure)
        assertTrue(runCatching { reducer.apply(low, LearningInteraction.SetAnswerConfidence("quiz", "missing", 1, ConfidenceLevel.LOW, ConfidenceLevel.HIGH), CommandActor.USER) }.isFailure)
    }
    @Test fun changingSelectedAnswerClearsReportButSameSelectionKeepsIt() {
        val high = confidence(selected(), ConfidenceLevel.HIGH)
        val reducer = LearningReducer()
        val same = reducer.reduce(high, LearningInteraction.SelectAnswer("quiz", "q1", 1))
        assertEquals(data(high).confidence, data(same).confidence)
        val changed = reducer.reduce(high, LearningInteraction.SelectAnswer("quiz", "q1", 0))
        assertTrue(data(changed).confidence.isEmpty()); assertEquals(0, data(changed).answers["q1"])
    }
    @Test fun clearingIsExplicitAndCheckingLocksReportedOrMissingConfidence() {
        val high = confidence(selected(), ConfidenceLevel.HIGH)
        val cleared = confidence(high, null, ConfidenceLevel.HIGH)
        assertTrue(data(cleared).confidence.isEmpty()); assertEquals(data(high).answers, data(cleared).answers)
        for (before in listOf(high, selected())) {
            val checked = LearningReducer().reduce(before, LearningInteraction.SubmitAnswer("quiz", "q1"))
            assertEquals(data(before).confidence, data(checked).confidence)
            assertTrue(runCatching { confidence(checked, ConfidenceLevel.LOW, data(before).confidence["q1"]?.level) }.isFailure)
            assertTrue(runCatching { confidence(checked, null, data(before).confidence["q1"]?.level) }.isFailure)
        }
    }
    @Test fun hiddenAndSuspendedQuizCannotAcquireANewReport() {
        val before = selected()
        val suspended = TestFixture.execute(before, WorkspaceCommand.SuspendActivity("quiz")).state
        assertTrue(runCatching { confidence(suspended, ConfidenceLevel.HIGH) }.isFailure)
        val hidden = before.copy(components = before.components.mapValues { it.value.copy(visible = false) })
        assertTrue(runCatching { confidence(hidden, ConfidenceLevel.HIGH) }.isFailure)
    }
    @Test fun restoredReportsMustBelongToAnActualSelectedAnswerAndCannotBeInjectedByAiCreation() {
        val quiz = data(selected())
        for (report in listOf(AnswerConfidence(ConfidenceLevel.HIGH, 0, 1), AnswerConfidence(ConfidenceLevel.HIGH, 1, -1)))
            assertTrue(runCatching { ActivityValidator.validate(quiz.copy(confidence = mapOf("q1" to report)), TestFixture.source, TestFixture.catalog) }.isFailure)
        val injected = quiz.copy(confidence = mapOf("q1" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 1)))
        val create = TestFixture.quiz().let { it as WorkspaceCommand.CreateActivity }.copy(data = injected)
        assertFalse(TestFixture.execute(TestFixture.reader(), create, policy = TestFixture.ai).trace.accepted)
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    @Test fun sharedWireAndAtomicBatchSupportExplicitConfidenceAndRejectStaleAnswer() {
        val report = AppCommand.SetAnswerConfidence("quiz", "q1", 1, null, ConfidenceLevel.HIGH)
        val batch = AppCommandBatch(requestId = "confidence", commands = listOf(AppCommand.SelectAnswer("quiz", "q1", 1), report, AppCommand.SubmitAnswer("quiz", "q1")))
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        assertEquals(report, LearningCommandBridge.command(LearningCommandBridge.event(report)!!))
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
        val valid = executor.execute(initial(), batch, TestFixture.ai)
        assertTrue(valid.trace.error, valid.trace.accepted); assertTrue(data(valid.state).confidence.getValue("q1").byAssistant)
        val invalid = executor.execute(initial(), batch.copy(commands = listOf(AppCommand.SelectAnswer("quiz", "q1", 0), report)), TestFixture.user)
        assertFalse(invalid.trace.accepted); assertEquals(initial(), invalid.state)
    }
    @Test fun layoutUndoCannotRollBackConfidenceOrCheckedAnswers() {
        val before = selected(); val undo = LayoutUndo().apply { record(before) }
        val high = confidence(before, ConfidenceLevel.HIGH)
        val checked = LearningReducer().reduce(high, LearningInteraction.SubmitAnswer("quiz", "q1"))
        assertEquals(data(checked), data(undo.restore(checked)!!))
    }
}
