package com.example.read

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.timer.PracticeTimerLifecycleObserver
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PracticeTimerLifecycleTest {
    @Test fun actualStopPausesAndReturningDoesNotResumeWithoutUserAction() = runBlocking {
        var now = 100L
        val fixture = PracticeTimerFixture({ now })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(AssistantTestActivity::class.java)
        try {
            scenario.onActivity { it.lifecycle.addObserver(PracticeTimerLifecycleObserver(fixture.workspace, scope)) }
            fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 1, 0))
            now += 800
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.waitForIdleSync()
            assertEquals(TimerStatus.PAUSED, fixture.data().timer.status)
            assertEquals(TimerChange.BACKGROUNDED, fixture.data().timer.lastChange)
            assertEquals(800L, fixture.data().timer.elapsedMillis)
            now += 10000
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            assertEquals(TimerStatus.PAUSED, fixture.data().timer.status)
            assertEquals(800L, fixture.data().timer.elapsedMillis)
            fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 1, fixture.data().timer.version))
            now += 200; fixture.workspace.tickTimers()
            assertEquals(1000L, fixture.data().timer.elapsedMillis)
        } finally { scenario.close(); scope.cancel() }
    }
}
