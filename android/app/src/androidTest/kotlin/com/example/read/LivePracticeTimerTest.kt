package com.example.read

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.*
import dev.folio.model.assistant.AssistantStatus
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Actual provider check against an isolated sample; credentials never leave device storage except provider authentication. */
class LivePracticeTimerTest {
    @Test fun geminiConfiguresStartsAndPausesWhileNativeTicksKeepRequestValid() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val fixture = PracticeTimerFixture(SystemClock::elapsedRealtime)
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val assistant = AssistantSession(fixture.workspace, AppCapabilityContext(fixture.curriculum, sourceCatalog = fixture.catalog),
            fixture.library, fixture.catalog, provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) })
        suspend fun ask(text: String) {
            assistant.ask(text)
            assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        val ticker = launch { while (isActive) { delay(100); fixture.workspace.tickTimers() } }
        try {
            ask("Set the current quiz timer to 1800 seconds. Keep my existing answer and do not start, reset or check anything.")
            assertEquals(1800000L, fixture.data().timer.limitMillis)
            assertEquals(TimerStatus.IDLE, fixture.data().timer.status)
            ask("Start my current practice timer now. Do not change my answer or check it.")
            assertEquals(TimerStatus.RUNNING, fixture.data().timer.status)
            delay(1250)
            ask("Pause my current practice timer now. Keep my counted time and answer unchanged.")
            val timer = fixture.data().timer
            assertEquals(TimerStatus.PAUSED, timer.status)
            assertTrue(timer.elapsedMillis >= 1000)
            assertTrue(timer.changedByAssistant)
            assertEquals(mapOf("timer-q" to 1), fixture.data().answers)
            assertTrue(fixture.data().submitted.isEmpty() && fixture.data().pastAttempts.isEmpty())
            assertTrue(fixture.workspace.traces.value.all { it.accepted })
            delay(250)
            assertEquals(timer, fixture.data().timer)
        } finally { ticker.cancelAndJoin() }
    }
}
