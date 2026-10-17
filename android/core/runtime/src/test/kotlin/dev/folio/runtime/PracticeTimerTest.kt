package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.workspace.*
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.practice.*
import org.junit.Assert.*
import org.junit.Test

class PracticeTimerTest {
    private var now = 100L
    private val coordinator = PracticeTimerCoordinator { now }
    private val reducer = PracticeTimerReducer()
    private fun initial(): Workspace = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state.let {
        it.copy(navigation = it.navigation.copy(destination = AppDestination.WORKSPACE))
    }
    private fun quiz(state: Workspace) = state.activities.getValue("quiz").data as ActivityData.Quiz
    private fun control(state: Workspace, event: LearningInteraction) = reducer.apply(state, event, CommandActor.USER)
    private fun start(state: Workspace = initial()) = control(state,
        LearningInteraction.StartPracticeTimer("quiz", 1, quiz(state).timer.version))

    @Test fun elapsedTicksPreserveSemanticRevisionAndExpiryDoesNotSubmitAnswers() {
        val configured = control(initial(), LearningInteraction.SetPracticeTimerLimit("quiz", 1, 0, 2))
        val running = coordinator.checkpoint(start(configured), true)
        now += 1000
        val halfway = coordinator.checkpoint(running, true)
        assertEquals(running.revision, halfway.revision)
        assertEquals(running.activities.keys, halfway.activities.keys)
        assertEquals(1000L, quiz(halfway).timer.elapsedMillis)
        assertEquals(quiz(running).timer.version, quiz(halfway).timer.version)
        now += 3000
        val expired = coordinator.checkpoint(halfway, true)
        assertEquals(TimerStatus.EXPIRED, quiz(expired).timer.status)
        assertEquals(2000L, quiz(expired).timer.elapsedMillis)
        assertEquals(halfway.revision + 1, expired.revision)
        assertTrue(quiz(expired).submitted.isEmpty())
        assertEquals(expired, coordinator.checkpoint(expired, true))
    }

    @Test fun backgroundPausesAtCheckpointAndRequiresExplicitResume() {
        val running = coordinator.checkpoint(start(), true)
        now += 700
        val paused = coordinator.checkpoint(running, false)
        assertEquals(TimerChange.BACKGROUNDED, quiz(paused).timer.lastChange)
        assertEquals(700L, quiz(paused).timer.elapsedMillis)
        now += 50000
        assertEquals(paused, coordinator.checkpoint(paused, true))
        val resumed = coordinator.checkpoint(start(paused), true)
        now += 300
        assertEquals(1000L, quiz(coordinator.checkpoint(resumed, true)).timer.elapsedMillis)
    }

    @Test fun assistantFromWorkspaceContinuesButHomeAndHiddenQuizPause() {
        val running = coordinator.checkpoint(start(), true)
        val assistant = running.copy(navigation = running.navigation.copy(
            destination = AppDestination.ASSISTANT, assistantOrigin = AppDestination.WORKSPACE))
        now += 100
        val chatting = coordinator.checkpoint(assistant, true)
        assertEquals(TimerStatus.RUNNING, quiz(chatting).timer.status)
        val home = coordinator.checkpoint(chatting.copy(navigation = AppNavigation()), true)
        assertEquals(TimerChange.LEFT_PRACTICE, quiz(home).timer.lastChange)
        val shown = coordinator.checkpoint(start(home.copy(navigation = running.navigation)), true)
        val hidden = shown.copy(components = shown.components.mapValues { (_, component) ->
            if (component.activityId == "quiz") component.copy(visible = false) else component
        })
        assertEquals(TimerChange.HIDDEN, quiz(coordinator.checkpoint(hidden, true)).timer.lastChange)
    }

    @Test fun recoveryDoesNotCountAwayTimeAndRollbackPausesClock() {
        val running = coordinator.checkpoint(start(), true)
        now += 250
        val saved = coordinator.checkpoint(running, true)
        now += 100000
        val recovered = PracticeTimerCoordinator { now }.recover(saved)
        assertEquals(250L, quiz(recovered).timer.elapsedMillis)
        assertEquals(TimerChange.RECOVERED, quiz(recovered).timer.lastChange)
        assertEquals(saved.revision + 1, recovered.revision)
        now = 0
        val interrupted = coordinator.checkpoint(saved, true)
        assertEquals(TimerChange.CLOCK_INTERRUPTED, quiz(interrupted).timer.lastChange)
        assertEquals(250L, quiz(interrupted).timer.elapsedMillis)
    }

    @Test fun staleControlsAndInvalidLimitsRejectWithoutChangingLearnerWork() {
        val selected = dev.folio.runtime.interaction.LearningReducer(TestFixture.catalog).reduce(initial(),
            LearningInteraction.SelectAnswer("quiz", "q1", 1))
        val running = start(selected)
        assertTrue(runCatching { control(running, LearningInteraction.ResetPracticeTimer("quiz", 1, 0)) }.isFailure)
        for (seconds in listOf(0, -1, 86401)) assertTrue(runCatching {
            control(running, LearningInteraction.SetPracticeTimerLimit("quiz", 1, 1, seconds))
        }.isFailure)
        val reset = control(running, LearningInteraction.ResetPracticeTimer("quiz", 1, 1))
        assertEquals(quiz(selected).answers, quiz(reset).answers)
        assertTrue(quiz(reset).submitted.isEmpty())
        assertEquals(TimerStatus.IDLE, quiz(reset).timer.status)
    }
}
