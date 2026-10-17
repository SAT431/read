package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

class PracticeTimerLargeTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun largeTextKeepsTimeControlsAndDurationEditorReachableWithoutChangingAnswers() = runBlocking {
        var now = 100L; val fixture = PracticeTimerFixture({ now })
        fixture.act(AppCommand.SetPracticeTimerLimit("timer-quiz", 1, 0, 30))
        fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 1, 1))
        now += 1000; fixture.workspace.tickTimers()
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
            val state by fixture.workspace.state.collectAsState()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                QuizRenderer().Render(ComponentRenderContext(state.components.getValue("timer-view"), state.activities.getValue("timer-quiz"), fixture.catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.preferences,
                    {}, { runBlocking { fixture.act(LearningCommandBridge.command(it)) } }, state.revisionItems))
            }
        } } }
        compose.onNodeWithTag("practice-timer-pause-timer-quiz").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("practice-timer-expand-timer-quiz").performScrollTo().performClick()
        compose.onNodeWithTag("practice-timer-limit-timer-quiz").performScrollTo().performClick()
        compose.onNodeWithTag("practice-timer-seconds").assertIsDisplayed().performTextReplacement("3")
        compose.onNodeWithTag("practice-timer-confirm-limit").assertIsDisplayed().performClick()
        compose.onNodeWithTag("practice-timer-reset-timer-quiz").performScrollTo().assertHasClickAction()
        compose.waitForIdle(); saveFixtureScreenshot("practice-timer-large-text.png")
        compose.onNodeWithTag("practice-timer-reset-timer-quiz").performClick()
        compose.onNodeWithText("Keep time").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(TimerStatus.PAUSED, fixture.data().timer.status)
            assertEquals(1000L, fixture.data().timer.elapsedMillis); assertEquals(3000L, fixture.data().timer.limitMillis)
            assertEquals(mapOf("timer-q" to 1), fixture.data().answers); assertTrue(fixture.data().submitted.isEmpty())
        }
    }
}
