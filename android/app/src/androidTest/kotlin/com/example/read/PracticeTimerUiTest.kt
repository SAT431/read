package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.activity.TimerStatus
import dev.folio.model.command.AppCommand
import dev.folio.model.port.MediaRepository
import dev.folio.runtime.action.LearningCommandBridge
import dev.folio.study.quiz.QuizRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PracticeTimerUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun countdownPauseResumeExpiryAndResetKeepAnswersUnchecked() {
        var now = 100L
        val fixture = PracticeTimerFixture({ now })
        compose.setContent { FolioTheme(false) {
            val state by fixture.workspace.state.collectAsState()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                QuizRenderer().Render(ComponentRenderContext(state.components.getValue("timer-view"), state.activities.getValue("timer-quiz"), fixture.catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.preferences,
                    { commands -> runBlocking { commands.forEach { fixture.act(AppCommand.WorkspaceAction(it)) } } },
                    { runBlocking { fixture.act(LearningCommandBridge.command(it)) } }, state.revisionItems))
            }
        } }
        compose.onNodeWithTag("practice-timer-limit-timer-quiz").assertDoesNotExist()
        compose.onNodeWithTag("practice-timer-expand-timer-quiz").performScrollTo().performClick()
        compose.onNodeWithTag("practice-timer-limit-timer-quiz").performScrollTo().performClick()
        compose.onNodeWithTag("practice-timer-seconds").performTextReplacement("2")
        compose.onNodeWithTag("practice-timer-confirm-limit").performClick()
        compose.onNodeWithTag("practice-timer-start-timer-quiz").performScrollTo().performClick()
        compose.runOnIdle { now += 1000; runBlocking { fixture.workspace.tickTimers() } }
        compose.onNodeWithTag("practice-timer-elapsed-timer-quiz").assertTextEquals("Elapsed 00:01")
        compose.onNodeWithTag("practice-timer-pause-timer-quiz").performClick()
        compose.runOnIdle { now += 10000; runBlocking { fixture.workspace.tickTimers() }; assertEquals(1000L, fixture.data().timer.elapsedMillis) }
        compose.onNodeWithTag("practice-timer-start-timer-quiz").performClick()
        compose.runOnIdle { now += 1500; runBlocking { fixture.workspace.tickTimers() } }
        compose.onNodeWithTag("practice-timer-status-timer-quiz").assertTextEquals("Time limit reached")
        compose.onNodeWithTag("practice-timer-start-timer-quiz").assertIsNotEnabled()
        compose.onNodeWithTag("practice-timer-reset-timer-quiz").performScrollTo().performClick()
        compose.onNodeWithText("Keep time").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.EXPIRED, fixture.data().timer.status) }
        compose.onNodeWithTag("practice-timer-reset-timer-quiz").performClick()
        compose.onNodeWithTag("practice-timer-confirm-reset").performClick()
        compose.onNodeWithTag("practice-timer-status-timer-quiz").assertTextEquals("Ready")
        compose.runOnIdle {
            assertEquals(2000L, fixture.data().timer.limitMillis)
            assertEquals(mapOf("timer-q" to 1), fixture.data().answers)
            assertTrue(fixture.data().submitted.isEmpty())
            assertTrue(fixture.data().pastAttempts.isEmpty())
        }
    }
}
