package com.example.read

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual app route/graph; changes navigation/orientation only and never logs learner state or keys. */
class MainActivityRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun productionGraphAndSavedLearningStateRemainStableAcrossOrientationRecreation() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("tab-learning").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("study-exit").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("study-exit").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("study-exit").performClick()
        compose.onNodeWithTag("tab-learning").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("learning-memory").fetchSemanticsNodes().isNotEmpty() }
        val original = compose.activity
        val request = original.requestedOrientation
        val graph = (original.application as FolioApplication).graph
        val state = graph.session.state.value
        val pdfs = graph.library.entries.value
        val wasPortrait = original.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val expected = if (wasPortrait) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = if (wasPortrait)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(20000) { compose.activity !== original && compose.activity.resources.configuration.orientation == expected }
            compose.onNodeWithTag("learning-memory").assertIsDisplayed()
            val currentGraph = (compose.activity.application as FolioApplication).graph
            assertSame(graph, currentGraph)
            val current = currentGraph.session.state.value
            // Constant assertion text keeps private source/answer/note contents out of failure output.
            assertTrue("Saved activities changed across rotation", state.activities == current.activities)
            assertTrue("Study source context changed across rotation", state.context == current.context)
            assertTrue("Revision state changed across rotation", state.revisionItems == current.revisionItems)
            assertTrue("Source corrections changed across rotation", state.sourceCorrections == current.sourceCorrections)
            assertTrue("PDF library changed across rotation", pdfs == currentGraph.library.entries.value)
            assertTrue("Learning route did not survive rotation", state.navigation == current.navigation)
            assertNull(currentGraph.session.storageError.value)
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = request } }
    }
}
