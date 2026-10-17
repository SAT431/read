package com.example.read

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.model.library.SubjectId
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** User-authorized installed Cell UI/screenshots. Never fabricates reading reports or exposes keys/notes. */
class LiveUploadedDocumentStudyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun uploadedBotanyCellPdfOpensFullScreenAndMapsItsRealOriginalPages() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        compose.waitUntil(20000) { compose.onAllNodesWithTag("tab-home").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("study-exit").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("study-exit").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("study-exit").performClick()
        val graph = (compose.activity.application as FolioApplication).graph
        val chapters = graph.curriculum.chapters().filter { it.subject == SubjectId.BOTANY && it.title.contains("Cell",ignoreCase=true) }
        val pdf = graph.library.entries.value.filter { entry -> chapters.any { it.id == entry.chapterId } }.maxByOrNull { it.importedAt }
        assertNotNull("The uploaded Botany Cell PDF was not found",pdf); val selected = requireNotNull(pdf)
        val before = graph.session.state.value.activities
        compose.onNodeWithTag("tab-home").performClick()
        compose.onNodeWithTag("subject-botany").performScrollTo().performClick()
        compose.onNodeWithTag("subject-chapters").performScrollToNode(hasTestTag("chapter-${selected.chapterId}"))
        compose.onNodeWithTag("chapter-${selected.chapterId}").performClick()
        compose.onNodeWithTag("chapter-library").performScrollToNode(hasTestTag("study-${selected.id}"))
        compose.onNodeWithTag("study-${selected.id}").performClick()
        compose.onNodeWithTag("study-companion").assertIsDisplayed()
        compose.onNodeWithTag("tab-home").assertDoesNotExist()
        compose.waitUntil(240000) { graph.documentIndexing.state.value[selected.document.id]?.let { !it.busy } == true }
        val work = graph.documentIndexing.state.value[selected.document.id]!!
        assertNull(work.error,work.error)
        val outline = graph.documentOutlines.get(selected.document.id)!!
        assertEquals(selected.document.pageCount,outline.indexedPages.size)
        assertTrue(outline.topics.isNotEmpty())
        assertTrue("Existing learner records changed",before.all { (id,value) ->
            val after = graph.session.state.value.activities[id] ?: return@all false
            val old = value.data as? dev.folio.model.activity.ActivityData.Reader
            val next = after.data as? dev.folio.model.activity.ActivityData.Reader
            if (old != null && next != null) next.copy(scrollIndex=old.scrollIndex,scrollOffset=old.scrollOffset,
                selectedSentence=old.selectedSentence,pagePositions=old.pagePositions) == old else after.data == value.data
        })
        assertTrue(java.io.File(selected.document.localPdfPath!!).exists())
        compose.onNodeWithTag("document-topics").assertIsDisplayed()
        compose.onNodeWithTag("study-chat-input").assertDoesNotExist()
        compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-installed-topics.png")
        val linked=outline.topics.first{it.firstPage>1}
        compose.onNodeWithTag("study-topic-list").performScrollToNode(hasTestTag("topic-original-${linked.id}"))
        compose.onNodeWithTag("topic-original-${linked.id}").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithTag("original-pdf-viewport").fetchSemanticsNodes().isNotEmpty()}
        val viewport=compose.onNodeWithTag("original-pdf-viewport").fetchSemanticsNode().boundsInWindow
        assertTrue("Original PDF viewport is crowded by controls",viewport.height>compose.activity.window.decorView.height*.6f)
        assertEquals(linked.firstPage,graph.session.state.value.navigation.pdfPageIndex)
        if(compose.onAllNodesWithTag("pdf-fit-width").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("pdf-fit-width").performClick()
            compose.runOnIdle{assertTrue(graph.session.state.value.navigation.pdfZoom>1f)}
            val beforePan=graph.session.state.value.navigation.pdfPanY
            compose.onNodeWithTag("original-pdf-viewport").performTouchInput{swipeUp()}
            compose.waitUntil(10000){graph.session.state.value.navigation.pdfPanY<beforePan}
        }
        compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-installed-original.png")
        compose.onNodeWithTag("app-back").performClick()
        compose.onNodeWithTag("document-topics").assertIsDisplayed()
        compose.onNodeWithTag("study-tools").performClick();compose.onNodeWithTag("study-sources").performClick()
        compose.onNodeWithTag("harness-source-${selected.id}").assertIsDisplayed()
        compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-installed-sources.png")
        compose.onNodeWithTag("study-sources-close").performClick()
        compose.onNodeWithTag("document-topics").assertIsDisplayed()
    }
}
