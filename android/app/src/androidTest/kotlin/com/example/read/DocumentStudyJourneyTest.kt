package com.example.read

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.folio.assistant.StudyConversationDock
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.study.*
import dev.folio.study.document.*
import dev.folio.local.study.AtomicDocumentOutlines
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DocumentStudyJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun chooseTopicPrepareOriginalImageReadAndExplicitlyConfirmWithoutTouchingOtherPage() = runBlocking {
        val f = PreparationDeviceFixture(ApplicationProvider.getApplicationContext<Context>(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        try {
            f.seed(); f.release.complete(Unit)
            val outline = DocumentOutline(f.pdf.document.id,f.checksum,2,listOf(0,1),listOf(
                DocumentTopic("amber","Amber grouping","Labelled text and original red figure",0,0,hasFigures=true),
                DocumentTopic("other","Other page","Different material",1,1)))
            f.outlines.save(outline); f.act(AppCommand.StudyPdf(f.pdf.id))
            compose.setContent { FolioTheme(false, reducedMotion = true) {
                val state by f.workspace.state.collectAsState(); val revision by f.pages.revision.collectAsState()
                val plan = state.studyPlans[f.pdf.id]
                val ready = remember(plan?.pages, revision) {
                    plan?.pages?.isNotEmpty() == true && plan.pages.all { f.catalog.page(f.pdf.document.id,it) != null }
                }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Text("Isolated full-screen study journey · controlled PDF")
                    Box(Modifier.weight(1f)) {
                        if (plan?.mode == StudyMode.READING && ready) SelectedTopicReader(plan,outline,f.catalog,f.media,20,revision) { runBlocking { f.act(it) } }
                        else DocumentTopicsScreen(f.pdf.id,outline,plan,DocumentStudyWork(message="Controlled fixture")) { runBlocking { f.act(it) } }
                    }
                    StudyConversationDock(f.pdf.id,AssistantState(),VoiceInputState(),true,plan?.takeIf { it.mode == StudyMode.READING },ready,
                        "Controlled line",{false},{}) { runBlocking { f.act(it) } }
                }
            } }
            compose.onNodeWithTag("study-topic-amber").performClick()
            compose.onNodeWithTag("study-prepare").assertIsEnabled().performClick()
            compose.waitUntil(20000) { f.catalog.page(f.pdf.document.id,0) != null }
            compose.onNodeWithTag("selected-topic-reader").assertExists()
            compose.onNodeWithTag("study-start-guide").performClick()
            compose.onNodeWithTag("study-finished-line").assertIsDisplayed()
            compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("document-study-guided-line.png")
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasContentDescription("Labelled original red figure"))
            compose.onNodeWithContentDescription("Labelled original red figure").assertIsDisplayed()
            compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("document-study-reader.png")
            compose.onNodeWithTag("study-finished-line").performClick()
            val readerId = "reader-${f.pdf.document.id}"
            val reader = f.workspace.state.value.activities.getValue(readerId).data as dev.folio.model.activity.ActivityData.Reader
            assertEquals(1,reader.readingConfirmations.size); assertFalse(f.workspace.state.value.studyPlans.getValue(f.pdf.id).guided)
            assertNull(f.pages.page(f.pdf.document.id,1)); assertEquals(1,f.engineCalls.get())
            assertTrue(f.original.activities.all { (id,value) -> f.workspace.state.value.activities[id] == value })
            assertArrayEquals(f.originalBytes,f.originalFile.readBytes()); f.workspace.flush()
            assertEquals(f.workspace.state.value.studyPlans,f.repository.load()!!.studyPlans)
            val reopened = AtomicDocumentOutlines(java.io.File(f.context.filesDir,"document-maps")); reopened.load(f.pdf.document)
            assertEquals(outline,reopened.get(f.pdf.document.id))
        } finally { f.close() }
    }
}
