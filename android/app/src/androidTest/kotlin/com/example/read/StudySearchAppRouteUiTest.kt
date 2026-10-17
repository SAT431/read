package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class StudySearchAppRouteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun productionHomeOpensNativeBackgroundChapterSearch() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("tab-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-home").performClick()
        compose.onNodeWithTag("home-search").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("study-search-query").performScrollTo().performTextReplacement("Laws of Motion")
        val graph = (compose.activity.application as FolioApplication).graph
        compose.waitUntil(20000) { graph.search.snapshotFor(graph.session.state.value)?.results?.any {
            it.target.kind == dev.folio.model.search.SearchKind.CHAPTER && it.title == "Laws of Motion"
        } == true }
        val target = graph.search.snapshotFor(graph.session.state.value)!!.results.first {
            it.target.kind == dev.folio.model.search.SearchKind.CHAPTER && it.title == "Laws of Motion"
        }
        val before = graph.session.state.value.activities
        compose.onNodeWithTag("study-search").performScrollToNode(hasTestTag("search-open-${target.fingerprint}"))
        compose.onNodeWithTag("search-open-${target.fingerprint}").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(dev.folio.model.workspace.AppDestination.CHAPTER, graph.session.state.value.navigation.destination)
        org.junit.Assert.assertEquals(target.target.ownerId, graph.session.state.value.navigation.chapterId)
        org.junit.Assert.assertTrue("Search navigation changed learning records", before == graph.session.state.value.activities)
        compose.onNodeWithTag("tab-home").performClick()
        compose.onNodeWithTag("home-search").assertExists()
    }
}
