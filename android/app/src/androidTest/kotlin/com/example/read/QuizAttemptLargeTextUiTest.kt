package com.example.read

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.action.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.practice.QuizHistoryReducer
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.study.quiz.QuizRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuizAttemptLargeTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun historicalChoicesStayReadOnlyAndCurrentAttemptRemainsReachableAtLargeText() {
        val catalog = ArchitectureSources.catalog(); val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog)
        val prompt = "Labelled saved-attempt question: read the original source before comparing this recorded choice with the key. ".repeat(3)
        val quiz = ActivityData.Quiz(listOf(Question("q", prompt, listOf("A", "B", "C", "D"), 0, "Labelled saved reference", listOf(source))),
            answers = mapOf("q" to 1), submitted = setOf("q"), confidence = mapOf("q" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 10)),
            checks = mapOf("q" to QuizCheck(20)))
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        val history = QuizHistoryReducer(catalog) { 30L }
        val next = history.apply(initial.state, LearningInteraction.RestartQuiz("quiz", 1), CommandActor.USER)
        val state = mutableStateOf(history.apply(next, LearningInteraction.ReviewQuizAttempt("quiz", 1, null), CommandActor.USER))
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val app = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        compose.setContent { FolioTheme(false) { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                QuizRenderer().Render(ComponentRenderContext(state.value.components.getValue("quiz-view"), state.value.activities.getValue("quiz"), catalog,
                    object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.value.preferences, {}, { event ->
                        val result = app.execute(state.value, AppCommandBatch(requestId = "layout", commands = listOf(LearningCommandBridge.command(event))), ExecutionPolicy(CommandActor.USER))
                        assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
                    }))
            }
        } } }
        compose.onNodeWithTag("quiz-history-option-q-1").performScrollTo().assertIsDisplayed().assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Confidence: Very sure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check answer").assertDoesNotExist()
        compose.onNodeWithTag("quiz-return-current").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "quiz-attempt-large-text.png").outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        compose.onNodeWithTag("quiz-return-current").performClick()
        compose.runOnIdle {
            val current = state.value.activities.getValue("quiz").data as ActivityData.Quiz
            assertNull(current.review); assertTrue(current.answers.isEmpty()); assertEquals(1, current.pastAttempts.size)
        }
    }
}
