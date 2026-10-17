package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Uses the installed production route; only changes navigation, never learner content or keys. */
class RevisionAppRouteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun productionRevisionTabOpensTheRealQueue() {
        compose.openSecondaryNavigation("tab-revision")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("revision-queue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("revision-queue").assertIsDisplayed()
    }
}
