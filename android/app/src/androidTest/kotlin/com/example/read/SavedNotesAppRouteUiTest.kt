package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SavedNotesAppRouteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun learningLinkOpensInstalledSavedNotesLibrary() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("tab-learning").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-learning").performClick()
        compose.onNodeWithTag("learning-open-notes").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("saved-notes").assertIsDisplayed()
        compose.onNodeWithTag("notes-filter-DRAFTS").assertHasClickAction()
    }
}
