package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.settings.SourceCacheRepairDialog
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.source.*
import com.example.read.fixture.ArchitectureSources
import dev.folio.catalog.Neet2026Catalog
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceCacheRepairUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun enlargedTextPreviewCancelNativeConfirmAndReceiptUseSharedActions() = runBlocking {
        var repairs = 0
        val store = object : SourceCacheRepairStore {
            override suspend fun previewRepair() = SourceCacheRepairPreview("labelled", 20, 2, 1)
            override suspend fun repair(expectedFingerprint: String): SourceCacheRepairReceipt {
                repairs++; return SourceCacheRepairReceipt("source-cache-labelled-fixture.jsonl", 2, 1, 18)
            }
        }
        val repair = SourceCacheRepairSession(store)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>()); override suspend fun load() {}
        }
        val executor = AppCommandExecutor(CommandExecutor(BuiltInComponents.registry(), ArchitectureSources.catalog()), Neet2026Catalog(), library, cacheRepair = repair)
        fun dispatch(command: AppCommand) = runBlocking {
            val result = executor.execute(Workspace(), AppCommandBatch(requestId = "fixture", commands = listOf(command)), ExecutionPolicy(CommandActor.USER))
            assertTrue(result.trace.error, result.trace.accepted)
            result.effects.forEach { when (it) {
                AppEffect.CheckSourceCache -> repair.request()
                is AppEffect.ConfirmSourceCacheRepair -> repair.confirm(it.token)
                AppEffect.DismissSourceCacheRepair -> repair.dismiss()
                else -> error("Unexpected fixture effect")
            } }
        }
        compose.setContent { FolioTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                val state by repair.state.collectAsState()
                Text("Isolated source-cache repair fixture")
                SourceCacheRepairDialog(state, ::dispatch)
            }
        } }
        compose.runOnIdle { dispatch(AppCommand.CheckSourceCache) }
        compose.onNodeWithTag("source-cache-confirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("source-cache-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, repairs); dispatch(AppCommand.CheckSourceCache) }
        compose.onNodeWithText("Only your tap below approves repair. Gemini cannot approve it.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("source-cache-confirm").assertIsDisplayed()
        compose.waitForIdle(); saveFixtureScreenshot("source-cache-repair-large-text.png")
        compose.onNodeWithTag("source-cache-confirm").performClick()
        compose.onNodeWithText("Cache repair complete.").assertExists()
        compose.onNodeWithText("Private backup saved: source-cache-labelled-fixture.jsonl").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("source-cache-close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, repairs); assertEquals(SourceCacheRepairPhase.CLOSED, repair.state.value.phase) }
    }
}
