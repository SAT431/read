package com.example.read

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.learning.LearningOverviewScreen
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.practice.LearningMemory
import dev.folio.runtime.registry.BuiltInComponents
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Enlarged-text native layout check, not full TalkBack acceptance. Sample records only. */
class LearningMemoryLargeTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun longObservationAndMemoryFiltersRemainReachableAtLargeText() {
        val catalog = ArchitectureSources.catalog(); val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val commands = CommandExecutor(BuiltInComponents.registry(), catalog)
        val prompt = "Labelled sample memory question: revisit this exact source and compare your checked answer with the saved reference. ".repeat(5)
        val quiz = ActivityData.Quiz(listOf(Question("q", prompt, listOf("A", "B", "C", "D"), 0, "Sample reference", listOf(source))),
            answers = mapOf("q" to 1), submitted = setOf("q"), confidence = mapOf("q" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 123, true)))
        val initial = commands.execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        assertTrue(initial.trace.error, initial.trace.accepted)
        val state = mutableStateOf(initial.state.copy(navigation = AppNavigation(AppDestination.LEARNING)))
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val executor = AppCommandExecutor(commands, Neet2026Catalog(), library, catalog)
        compose.setContent { FolioTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                LearningOverviewScreen(state.value, catalog) { command ->
                    val result = executor.execute(state.value, AppCommandBatch(requestId = "layout", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
                    assertTrue(result.trace.error, result.trace.accepted); state.value = result.state
                }
            }
        } }
        compose.onNodeWithTag("learning-filter-CONFIDENT_MISTAKES").performScrollTo().performClick()
        compose.onNodeWithTag("learning-count").assertTextEquals("1 saved observation")
        val observation = LearningMemory.observations(state.value).single()
        compose.onNodeWithTag("learning-open-${observation.id}").performScrollTo().assertIsDisplayed().assertHasClickAction().assertIsEnabled()
        compose.onNodeWithText("Confidence: Very sure").assertExists()
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "learning-memory-large-text.png").outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        compose.onNodeWithTag("learning-filter-UNCERTAIN_CORRECT").performScrollTo().performClick()
        compose.onNodeWithTag("learning-count").assertTextEquals("0 saved observations")
    }
}
