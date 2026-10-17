package com.example.read

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.timer.PracticeTimerLifecycleObserver
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import dev.folio.runtime.reading.SentenceIndex
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PracticeTimerRotationTest {
    @Test fun orientationChangeRecreatesHostButKeepsRunningTimeAndSavedTargets() = runBlocking {
        val fixture = PracticeTimerFixture(SystemClock::elapsedRealtime)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(AssistantTestActivity::class.java)
        var originalRequest = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            var original: AssistantTestActivity? = null
            var target = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            var expected = Configuration.ORIENTATION_PORTRAIT
            scenario.onActivity { owner ->
                originalRequest = owner.requestedOrientation; original = owner
                if (owner.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    target = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; expected = Configuration.ORIENTATION_LANDSCAPE
                }
                owner.lifecycle.addObserver(PracticeTimerLifecycleObserver(fixture.workspace, scope) { owner.isChangingConfigurations })
            }
            val sentence = SentenceIndex().compile(requireNotNull(fixture.catalog.page("fixture-cell", 0))).first()
            fixture.act(AppCommand.AddReadingBookmark("reader", "rotation-bookmark", sentence.source, sentence.id))
            fixture.act(AppCommand.ConfirmReading("reader", sentence.source, expectedReadingRevision = 0))
            fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 1, 0))
            val reader = fixture.workspace.state.value.activities.getValue("reader").data
            delay(250)
            scenario.onActivity { it.requestedOrientation = target }
            withTimeout(20000) {
                while (true) {
                    var recreated = false
                    scenario.onActivity { recreated = it !== original && it.resources.configuration.orientation == expected }
                    if (recreated) break
                    delay(100)
                }
            }
            scenario.onActivity { owner ->
                owner.lifecycle.addObserver(PracticeTimerLifecycleObserver(fixture.workspace, scope) { owner.isChangingConfigurations })
            }
            instrumentation.waitForIdleSync(); fixture.workspace.tickTimers()
            assertEquals(TimerStatus.RUNNING, fixture.data().timer.status)
            assertEquals(TimerChange.STARTED, fixture.data().timer.lastChange)
            assertEquals(1, fixture.data().timer.version)
            assertTrue(fixture.data().timer.elapsedMillis >= 250)
            assertEquals(mapOf("timer-q" to 1), fixture.data().answers)
            assertEquals(reader, fixture.workspace.state.value.activities.getValue("reader").data)
            scenario.moveToState(Lifecycle.State.CREATED); instrumentation.waitForIdleSync()
            assertEquals(TimerChange.BACKGROUNDED, fixture.data().timer.lastChange)
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { it.requestedOrientation = originalRequest }
            scenario.close(); scope.cancel()
        }
    }
}
