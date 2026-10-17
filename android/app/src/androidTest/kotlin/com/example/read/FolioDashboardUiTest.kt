package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.material3.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.example.read.navigation.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.home.HomeScreen
import dev.folio.model.command.AppCommand
import dev.folio.model.library.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.workspace.AppDestination
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Labelled temporary dashboard content only; never screenshots learner PDFs, keys or notes. */
class FolioDashboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun compactDashboardShowsSubjectsAndRecentPdfsWithRealNavigationCommands() = checkDashboard(false, 1f)
    @Test fun enlargedDarkDashboardKeepsSecondaryNavigationAndSubjectTargetsReachable() = checkDashboard(true, 1.8f)
    @Test fun wideSidebarKeepsHomeAndSecondaryToolsSeparateFromStudyContent() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(20000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.setContent { FolioTheme(false) { Surface {
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    NavigationSidebar(AppDestination.HOME) {}
                    Column(Modifier.weight(1f)) {
                        AppTopBar(AppDestination.HOME, {}, wide = true)
                        HomeScreen(Neet2026Catalog(), emptyList()) {}
                    }
                }
            } } }
            compose.onNodeWithTag("tab-home").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithTag("tab-settings").assertIsDisplayed()
            compose.onNodeWithTag("subject-zoology").assertIsDisplayed()
            compose.onNodeWithTag("app-more").assertDoesNotExist()
            compose.waitForIdle(); Thread.sleep(1000); saveFixtureScreenshot("dashboard-wide.png")
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
    private fun checkDashboard(dark: Boolean, scale: Float) {
        val previous = compose.activity.requestedOrientation
        try {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(20000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        val commands = mutableListOf<AppCommand>()
        val pdf = LibraryPdf("fixture-recent", "fixture-chapter", SourceDocument("fixture-document", "Labelled Biology notes.pdf", 4, "fixture-checksum", "fixture.pdf"), 10)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                FolioTheme(dark, reducedMotion = dark) {
                    Surface {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            AppTopBar(AppDestination.HOME, commands::add)
                            Box(Modifier.weight(1f)) { HomeScreen(Neet2026Catalog(), listOf(pdf), commands::add) }
                            CompactNavigation(AppDestination.HOME, commands::add)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (!dark) compose.onNodeWithTag("subject-zoology").assertIsDisplayed()
        Thread.sleep(1000) // Android's orientation surface transition can outlast Compose's idle state.
        saveFixtureScreenshot(if (dark) "dashboard-dark-large-text.png" else "dashboard-light.png")
        compose.onNodeWithTag("app-more").performClick()
        compose.onNodeWithTag("tab-settings").assertIsDisplayed().performClick()
        assertEquals(AppCommand.Navigate(AppDestination.SETTINGS), commands.last())
        compose.onNodeWithTag("subject-botany").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(AppCommand.OpenSubject(SubjectId.BOTANY), commands.last())
        compose.onNodeWithTag("recent-fixture-recent").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(AppCommand.OpenPdf(pdf.id), commands.last())
        compose.onNodeWithTag("home-search").performScrollTo().performClick()
        assertEquals(AppCommand.Navigate(AppDestination.SEARCH), commands.last())
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = previous } }
    }
}
