package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Secondary destinations live in More on compact screens and directly in the wide sidebar. */
internal fun ComposeContentTestRule.openSecondaryNavigation(tag: String) {
    waitUntil(20000) { onAllNodesWithTag("tab-home").fetchSemanticsNodes().isNotEmpty() }
    if (onAllNodesWithTag("app-more").fetchSemanticsNodes().isNotEmpty()) onNodeWithTag("app-more").performClick()
    onNodeWithTag(tag).performClick()
}
