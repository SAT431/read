package com.example.read

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.ArchitectureSources
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.revision.RevisionQueueScreen
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.practice.RevisionTargets
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Layout/semantics check at enlarged system text; does not claim TalkBack acceptance. */
class RevisionQueueLargeTextUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun longReviewAtLargeTextKeepsOpenCompleteAndRemoveActionsReachable() {
        val catalog = ArchitectureSources.catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val prompt = "Labelled sample review: explain the relationship between the source passage and this recall prompt. ".repeat(8)
        val activity = Activity("large-recall", source, ActivityData.Recall(prompt, "Sample reference", listOf(source), revealed = true, rating = RecallRating.RECALLED, ratedAtEpochMillis = 100L))
        val id = RevisionTargets.id(activity.id, null)
        val item = RevisionItem(id, activity.id, null, source, prompt, 100, 10, 10)
        val state = Workspace(activities = mapOf(activity.id to activity), revisionItems = mapOf(id to item))
        val actions = mutableListOf<AppCommand>()
        compose.setContent { FolioTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                RevisionQueueScreen(state, catalog, { actions.add(it) }, 200L)
            }
        } }
        compose.onNodeWithText("1 due · 0 upcoming · 0 completed").assertExists()
        compose.onNodeWithTag("revision-open-$id").performScrollTo().assertIsDisplayed().assertHasClickAction().assertIsEnabled()
        compose.onNodeWithTag("revision-open-$id").performClick()
        compose.runOnIdle { assertEquals(listOf(AppCommand.OpenRevision(id)), actions) }
        compose.onNodeWithTag("revision-complete-$id").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("revision-suggestion-${activity.id}-recall").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "revision-queue-large-text.png").outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        compose.onNodeWithTag("revision-remove-$id").performScrollTo().performClick()
        compose.onNodeWithTag("revision-confirm-remove").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithTag("revision-open-$id").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("revision-suggestion-${activity.id}-recall").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.waitForIdle()
    }
}
