package com.example.read

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.folio.assistant.ProviderKeyEditor
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.model.port.GeminiModels
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GroupedStudyControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun pageToolsZoomResetAndValidatedJumpPreserveSourceAndLearnerWork() = runBlocking {
        val fixture = PreparationDeviceFixture(ApplicationProvider.getApplicationContext<Context>(),
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        try {
            fixture.seed()
            compose.setContent { FolioTheme(false) {
                val state by fixture.workspace.state.collectAsState()
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Text("Isolated original-PDF controls fixture")
                    OriginalPdfScreen(fixture.pdf, state.navigation, fixture.tools, fixture.media) { runBlocking { fixture.act(it) } }
                }
            } }
            compose.onNodeWithTag("pdf-previous").assertIsNotEnabled()
            compose.onNodeWithTag("pdf-zoom-in").assertDoesNotExist()
            compose.onNodeWithTag("pdf-tools").performClick()
            compose.onNodeWithTag("pdf-zoom-in").performClick()
            assertEquals(1.25f, fixture.workspace.state.value.navigation.pdfZoom)
            compose.onNodeWithTag("pdf-tools").performClick()
            compose.onNodeWithTag("pdf-zoom-reset").performClick()
            assertEquals(1f, fixture.workspace.state.value.navigation.pdfZoom)
            compose.onNodeWithTag("pdf-tools").performClick()
            compose.onNodeWithTag("pdf-jump").performClick()
            compose.onNodeWithTag("pdf-page-input").performTextReplacement("99")
            compose.onNodeWithTag("pdf-page-go").assertIsNotEnabled()
            assertEquals(0, fixture.workspace.state.value.navigation.pdfPageIndex)
            compose.onNodeWithTag("pdf-page-input").performTextReplacement("2")
            compose.onNodeWithTag("pdf-page-go").assertIsEnabled().performClick()
            compose.onNodeWithTag("pdf-page-input").assertDoesNotExist()
            compose.onNodeWithTag("pdf-next").assertIsNotEnabled()
            assertEquals(1, fixture.workspace.state.value.navigation.pdfPageIndex)
            compose.onNodeWithTag("pdf-previous").performClick()
            assertEquals(0, fixture.workspace.state.value.navigation.pdfPageIndex)
            assertTrue(fixture.original.activities == fixture.workspace.state.value.activities)
            assertArrayEquals(fixture.originalBytes, fixture.originalFile.readBytes())
            fixture.workspace.flush()
            assertTrue(fixture.original.activities == fixture.repository.load()!!.activities)
        } finally { fixture.close() }
    }
    @Test fun foldedConfiguredProviderInputsRetainEditsUntilExplicitSave() {
        var saved = false
        compose.setContent { FolioTheme(false, reducedMotion = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("Isolated provider-input fixture · synthetic values only")
                    ProviderKeyEditor(GeminiModels.CURRENT_FLASH, true, false, null, { gemini, groq, model ->
                        saved = gemini == "fixture-gemini" && groq == "fixture-groq" && model == GeminiModels.CURRENT_FLASH
                    }, {})
                } }
            }
        } }
        compose.onNodeWithTag("gemini-key").assertDoesNotExist()
        compose.onNodeWithTag("provider-toggle").performClick()
        compose.onNodeWithTag("gemini-key").performScrollTo().performTextInput("fixture-gemini")
        compose.onNodeWithTag("groq-key").performScrollTo().performTextInput("fixture-groq")
        compose.onNodeWithTag("provider-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("gemini-key").assertDoesNotExist()
        compose.onNodeWithTag("provider-toggle").performClick()
        compose.onNodeWithTag("provider-save").performScrollTo().performClick()
        compose.runOnIdle { assertTrue("Folding discarded an input or changed the selected model", saved) }
    }
}
