package com.example.read

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import dev.folio.assistant.StudyConversationDock
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.assistant.*
import dev.folio.model.study.*
import dev.folio.study.document.DocumentTopicsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated prebuilt topic/chat UI at enlarged text with the actual Android keyboard. */
class StudyCompanionUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun enlargedPortraitChatKeepsSendVisibleAndRetainsFoldedDraft() {
        val previous = compose.activity.requestedOrientation
        val sent = mutableListOf<String>()
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(20000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.activityRule.scenario.onActivity { it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            val outline = DocumentOutline("fixture-document","fixture-checksum",1,listOf(0),listOf(
                DocumentTopic("fixture-topic","Controlled topic","Labelled UI fixture only",0,0)))
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
                    FolioTheme(false,reducedMotion = true) {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                            Text("Isolated study companion · enlarged text")
                            Box(Modifier.weight(1f)) { DocumentTopicsScreen("fixture-pdf",outline,null,DocumentStudyWork()) {} }
                            StudyConversationDock("fixture-pdf",AssistantState(),VoiceInputState(),true,null,false,null,
                                { sent.add(it); true },{}) {}
                        }
                    }
                }
            }
            compose.onNodeWithTag("study-chat-input").assertDoesNotExist()
            compose.onNodeWithTag("study-chat-toggle").performClick()
            compose.onNodeWithTag("study-chat-input").performClick().performTextInput("Study the controlled topic")
            compose.waitUntil(10000) { compose.runOnIdle {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            } }
            compose.waitForIdle(); Thread.sleep(1000)
            compose.onNodeWithTag("study-chat-send").assertIsDisplayed().assertIsEnabled()
            val sendBottom = compose.onNodeWithTag("study-chat-send").fetchSemanticsNode().boundsInWindow.bottom
            val keyboardTop = compose.runOnIdle {
                val decor = compose.activity.window.decorView
                decor.height - requireNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
            assertTrue("Send is covered by the keyboard",sendBottom <= keyboardTop)
            saveFixtureScreenshot("document-study-companion-keyboard.png")
            compose.activityRule.scenario.onActivity {
                WindowInsetsControllerCompat(it.window,it.window.decorView).hide(WindowInsetsCompat.Type.ime())
            }
            compose.waitForIdle(); Thread.sleep(500)
            compose.onNodeWithTag("study-chat-close").performClick()
            compose.onNodeWithTag("study-chat-input").assertDoesNotExist()
            compose.onNodeWithTag("study-speak").assertIsDisplayed()
            compose.onNodeWithTag("study-chat-toggle").performClick()
            compose.onNodeWithTag("study-chat-input").assertTextContains("Study the controlled topic")
            compose.onNodeWithTag("study-chat-send").performClick()
            assertEquals(listOf("Study the controlled topic"),sent)
            compose.onNodeWithTag("study-chat-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
            compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("document-study-companion-large-text.png")
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
}
