package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.model.assistant.AssistantStatus
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.*
import org.junit.Test

class PracticeTimerSessionTest {
    private var now = 100L
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<Subject>()
        override fun chapters() = emptyList<StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>())
        override suspend fun load() {}
    }
    private class Store(var saved: Workspace? = null) : WorkspaceRepository {
        override suspend fun load() = saved
        override suspend fun save(workspace: Workspace) { saved = workspace }
    }
    private fun initial() = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state.let {
        it.copy(navigation = AppNavigation(AppDestination.WORKSPACE))
    }
    private fun session(store: Store = Store(), foreground: Boolean = true) = WorkspaceSession(initial(), TestFixture.executor,
        store, WorkspaceValidator(TestFixture.registry, TestFixture.catalog),
        appExecutor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog),
        timerClock = { now }, initialForeground = foreground)
    private fun data(session: WorkspaceSession) = session.state.value.activities.getValue("quiz").data as ActivityData.Quiz
    private suspend fun act(session: WorkspaceSession, command: AppCommand, revision: Long? = null) = session.dispatchApp(
        AppCommandBatch(requestId = "timer", expectedRevision = revision, commands = listOf(command)), TestFixture.ai)

    @Test fun capturedAiRevisionSurvivesTicksAndPausePersistsLatestElapsedTime() = runBlocking {
        val job = SupervisorJob(); val store = Store(); val session = session(store)
        session.attachWriter(CoroutineScope(coroutineContext + job))
        try {
            assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 0)).trace.accepted)
            val revision = session.state.value.revision
            now += 1250; session.tickTimers()
            assertEquals(revision, session.state.value.revision)
            assertTrue(act(session, AppCommand.PausePracticeTimer("quiz", 1, 1), revision).trace.accepted)
            assertEquals(1250L, data(session).timer.elapsedMillis)
            assertEquals(TimerStatus.PAUSED, data(session).timer.status)
            assertTrue(data(session).timer.changedByAssistant)
            assertEquals(session.state.value, store.saved)
        } finally { job.cancelAndJoin() }
    }

    @Test fun expiryInvalidatesCapturedCommandButKeepsAnswersAndDoesNotRestart() = runBlocking {
        val session = session()
        assertTrue(act(session, AppCommand.SelectAnswer("quiz", "q1", 1)).trace.accepted)
        assertTrue(act(session, AppCommand.SetPracticeTimerLimit("quiz", 1, 0, 1)).trace.accepted)
        assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 1)).trace.accepted)
        val revision = session.state.value.revision
        now += 1100
        assertFalse(act(session, AppCommand.ResetPracticeTimer("quiz", 1, 2), revision).trace.accepted)
        assertEquals(TimerStatus.EXPIRED, data(session).timer.status)
        assertEquals(mapOf("q1" to 1), data(session).answers)
        assertTrue(data(session).submitted.isEmpty())
    }

    @Test fun backgroundRejectsStartAndRecoveryKeepsDurableCheckpointWithoutAwayTime() = runBlocking {
        val job = SupervisorJob(); val store = Store(); val session = session(store)
        session.attachWriter(CoroutineScope(coroutineContext + job))
        try {
            assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 0)).trace.accepted)
            now += 600; session.flush()
            val saved = requireNotNull(store.saved)
            assertEquals(600L, data(session).timer.elapsedMillis)
            now += 100000
            val recovered = session(store, false)
            recovered.restore()
            assertEquals(TimerChange.RECOVERED, data(recovered).timer.lastChange)
            assertEquals(600L, data(recovered).timer.elapsedMillis)
            assertEquals(saved.revision + 1, recovered.state.value.revision)
            assertFalse(act(recovered, AppCommand.StartPracticeTimer("quiz", 1, data(recovered).timer.version)).trace.accepted)
            recovered.setForeground(true)
            assertTrue(act(recovered, AppCommand.StartPracticeTimer("quiz", 1, data(recovered).timer.version)).trace.accepted)
            now += 400; recovered.setForeground(false)
            assertEquals(1000L, data(recovered).timer.elapsedMillis)
            assertEquals(TimerChange.BACKGROUNDED, data(recovered).timer.lastChange)
        } finally { job.cancelAndJoin() }
    }

    @Test fun restartingQuizArchivesCheckpointAndCreatesFreshTimer() = runBlocking {
        val session = session()
        assertTrue(act(session, AppCommand.SelectAnswer("quiz", "q1", 0)).trace.accepted)
        assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 0)).trace.accepted)
        now += 800
        assertTrue(act(session, AppCommand.RestartQuiz("quiz", 1)).trace.accepted)
        val past = data(session).pastAttempts.single()
        assertEquals(800L, past.timer?.elapsedMillis)
        assertEquals(TimerChange.ARCHIVED, past.timer?.lastChange)
        assertEquals(TimerStatus.PAUSED, past.timer?.status)
        assertEquals(mapOf("q1" to 0), past.answers)
        assertEquals(PracticeTimer(), data(session).timer)
        now += 10000; session.tickTimers()
        assertEquals(PracticeTimer(), data(session).timer)
    }

    @Test fun assistantResponseAfterTicksCommitsThroughNormalContextAndActionValidation() = runBlocking {
        val session = session()
        assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 0)).trace.accepted)
        val assistant = AssistantSession(session, AppCapabilityContext(curriculum), library, TestFixture.catalog,
            provider = { object : MentorProvider {
                override suspend fun respond(request: MentorRequest): MentorResponse {
                    now += 2000; session.tickTimers()
                    return MentorResponse(requestId = request.requestId, message = "Fixture pause", batch = AppCommandBatch(
                        requestId = request.requestId, expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long,
                        commands = listOf(AppCommand.PausePracticeTimer("quiz", 1, 1))))
                }
            } }, effects = { assertTrue(it.isEmpty()) })
        assistant.ask("Pause my timer")
        assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        assertEquals(2000L, data(session).timer.elapsedMillis)
        assertEquals(TimerStatus.PAUSED, data(session).timer.status)
        assertTrue(data(session).timer.changedByAssistant)
    }

    @Test fun expansionKeepsClockAnchorAndLearnerDataWhileSuspensionPauses() = runBlocking {
        val session = session()
        assertTrue(act(session, AppCommand.StartPracticeTimer("quiz", 1, 0)).trace.accepted)
        now += 250
        assertTrue(act(session, AppCommand.SetPracticeTimerExpanded("quiz", 1, 1, true)).trace.accepted)
        assertTrue(data(session).timer.detailsExpanded)
        assertEquals(1, data(session).timer.version)
        now += 250; session.tickTimers()
        assertEquals(500L, data(session).timer.elapsedMillis)
        assertTrue(act(session, AppCommand.WorkspaceAction(WorkspaceCommand.SuspendActivity("quiz"))).trace.accepted)
        assertEquals(TimerChange.SUSPENDED, data(session).timer.lastChange)
        now += 5000
        assertTrue(act(session, AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("quiz"))).trace.accepted)
        assertEquals(TimerStatus.PAUSED, data(session).timer.status)
        assertEquals(500L, data(session).timer.elapsedMillis)
    }
}
