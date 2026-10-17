package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

/** Production navigation/check only; never confirms repair or reads source payloads/keys. */
class SourceCacheRepairAppRouteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualAppearanceControlOpensCacheCheckAndDismissesWithoutRepair() {
        compose.openSecondaryNavigation("tab-settings")
        compose.onNodeWithTag("source-cache-check").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("source-cache-close").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("source-cache-cancel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("source-cache-repair-dialog").assertExists()
        if (compose.onAllNodesWithTag("source-cache-close").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("source-cache-close").performClick()
        else compose.onNodeWithTag("source-cache-cancel").performClick()
        compose.onNodeWithTag("source-cache-repair-dialog").assertDoesNotExist()
    }
}
