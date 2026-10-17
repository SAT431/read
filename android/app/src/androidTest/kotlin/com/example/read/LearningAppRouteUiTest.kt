package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Changes navigation only; does not reset learner work or inspect key fields. */
class LearningAppRouteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun installedLearningTabOpensTheProductionOverview() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("tab-learning").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-learning").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("learning-memory").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("learning-memory").assertIsDisplayed()
    }
}
