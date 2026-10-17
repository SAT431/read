package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Run on a connected emulator/tablet. These exercise actual prebuilt native renderers. */
class ArchitectureSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun explorerCreatesQuestionAndRetainsSelectedAnswerAcrossSourceReview() {
        compose.openSecondaryNavigation("tab-explorer")
        compose.onNodeWithText("Prebuilt interface universe").assertExists()
        compose.onNodeWithTag("component-explorer").performScrollToNode(hasText("Add Question practice"))
        compose.onNodeWithText("Add Question practice").performClick()
        compose.onNodeWithText("Surface ribosomes", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Pause and review source").performScrollTo().performClick()
        compose.onNodeWithText("quiz · suspended").performClick()
        compose.onNodeWithText("Surface ribosomes", substring = true).performScrollTo().assertIsSelected()
    }
}
