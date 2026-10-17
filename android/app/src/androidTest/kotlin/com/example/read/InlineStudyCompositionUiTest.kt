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
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.*
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.source.SourceDocument
import dev.folio.model.study.*
import dev.folio.runtime.study.*
import dev.folio.study.document.SelectedTopicReader
import dev.folio.model.port.MediaRepository
import dev.folio.ui.media.LocalSourceRevision
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Controlled originals only: insertion, source ownership, retained viewport and guided focus. */
class InlineStudyCompositionUiTest {
    @get:Rule val compose=createAndroidComposeRule<AssistantTestActivity>()
    @Test fun importedMaterialAppearsInsideCurrentPageWithoutResettingViewportOrReportingItRead()=runBlocking {
        val f=PreparationDeviceFixture(ApplicationProvider.getApplicationContext<Context>(),CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate))
        try {
            f.seed();f.release.complete(Unit)
            val copy=File(f.originalFile.parentFile,"other.pdf");f.originalFile.copyTo(copy)
            copy.appendText("\n% Labelled second-source fixture\n")
            val secondBytes=copy.readBytes()
            val checksum=java.security.MessageDigest.getInstance("SHA-256").digest(secondBytes).joinToString(""){"%02x".format(it)}
            val second=LibraryPdf("inline-second",f.pdf.chapterId,SourceDocument("inline-second-doc","Other controlled PDF",2,checksum,copy.path),2)
            f.library.add(second)
            val map=DocumentOutline(f.pdf.document.id,f.checksum,2,listOf(0,1),listOf(DocumentTopic("base","Controlled base","Original content",0,0,hasFigures=true)))
            val extra=map.copy(documentId=second.document.id,checksum=checksum,topics=listOf(DocumentTopic("extra","Controlled addition","Source content",0,0,hasFigures=true)))
            f.outlines.save(map);f.outlines.save(extra)
            f.act(AppCommand.HarnessOpenTopic(f.pdf.id,DocumentStudyRules.fingerprint(map),"base"))
            awaitReady(f)
            val reads=java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger>()
            val media=object:MediaRepository{override suspend fun read(mediaId:String):ByteArray?{
                reads.getOrPut(mediaId){java.util.concurrent.atomic.AtomicInteger()}.incrementAndGet();return f.media.read(mediaId)
            }}
            compose.setContent { FolioTheme(false,reducedMotion=true) {
                val state by f.workspace.state.collectAsState();val maps by f.outlines.outlines.collectAsState()
                val pdfs by f.library.entries.collectAsState();val revision by f.pages.revision.collectAsState()
                val plan=state.studyPlans.getValue(f.pdf.id)
                val sources=remember(plan,maps,pdfs){StudySources.resolve(plan,pdfs,f.outlines)}
                val ready=remember(sources,revision){StudySources.ready(sources,f.catalog)}
                CompositionLocalProvider(LocalSourceRevision provides revision) { Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Text("Isolated inline PDF composition · controlled sources")
                    Box(Modifier.weight(1f)) { SelectedTopicReader(plan,map,f.catalog,media,20,revision,sources){runBlocking{f.act(it)}} }
                    StudyConversationDock(f.pdf.id,AssistantState(),VoiceInputState(),true,plan,ready,"Controlled line",{false},{}){runBlocking{f.act(it)}}
                } }
            } }
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("source-figure-prepared-figure"))
            compose.waitUntil(10000){compose.onAllNodesWithTag("source-figure-ready-prepared-figure").fetchSemanticsNodes().isNotEmpty()}
            compose.waitForIdle()
            val before=compose.onNodeWithTag("source-figure-ready-prepared-figure").fetchSemanticsNode().boundsInWindow.top
            val baseMedia=f.catalog.page(f.pdf.document.id,0)!!.elements.first{it.id=="prepared-figure"}.mediaId!!
            val baseReads=reads.getValue(baseMedia).get()
            saveFixtureScreenshot("study-inline-before.png")
            f.act(AppCommand.HarnessIncludeTopics(f.pdf.id,second.id,f.workspace.state.value.studyPlans.getValue(f.pdf.id).version,
                DocumentStudyRules.fingerprint(extra),listOf("extra")))
            awaitReady(f);compose.waitForIdle()
            val current=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(StudyPlacement(f.pdf.id,0),current.attachedSources.single().placement)
            assertEquals(f.pdf.document.id,f.workspace.state.value.context.source!!.documentId)
            // The original visible figure remains in place as new rows are appended below it.
            val after=compose.onAllNodesWithTag("source-figure-ready-prepared-figure").fetchSemanticsNodes().first().boundsInWindow.top
            saveFixtureScreenshot("study-inline-after.png")
            assertEquals(before,after,2f)
            assertEquals("Unrelated cache update decoded the visible image again",baseReads,reads.getValue(baseMedia).get())
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("study-inline-source-${second.id}-0"))
            compose.onNodeWithTag("study-inline-source-${second.id}-0").assertIsDisplayed()
            assertEquals(f.pdf.id,f.workspace.state.value.navigation.pdfId)
            assertTrue(f.workspace.state.value.activities.values.all{(it.data as? ActivityData.Reader)?.readingConfirmations.isNullOrEmpty()})
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("study-inline-added-material.png")
            compose.onNodeWithTag("study-source-focus-${second.id}-0").performClick()
            assertEquals(second.document.id,f.workspace.state.value.context.source!!.documentId)
            val focused=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(f.pdf.id,StudyComposition.surface(focused,StudySources.resolve(focused,f.library.entries.value,f.outlines)).root.source.pdf.id)
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("study-enlarge-${second.document.id}-prepared-figure"))
            compose.onNodeWithTag("study-enlarge-${second.document.id}-prepared-figure").performClick()
            assertEquals(second.id,f.workspace.state.value.navigation.pdfId)
            assertEquals(0,f.workspace.state.value.navigation.pdfPageIndex)
            f.act(AppCommand.Back)
            assertEquals(f.pdf.id,f.workspace.state.value.navigation.pdfId)
            compose.onNodeWithTag("study-start-guide").performClick()
            compose.onNodeWithTag("study-finished-line").performClick()
            val guided=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(second.id,guided.activeSourcePdfId)
            assertEquals(f.pdf.id,f.workspace.state.value.navigation.pdfId)
            val readers=f.workspace.state.value.activities
            assertTrue((readers.getValue("reader-${f.pdf.document.id}").data as ActivityData.Reader).readingConfirmations.isEmpty())
            assertEquals(1,(readers.getValue("reader-${second.document.id}").data as ActivityData.Reader).readingConfirmations.size)
            assertTrue(f.original.activities.all{(id,value)->readers[id]==value})
            f.workspace.flush();assertEquals(f.workspace.state.value.studyPlans,f.repository.load()!!.studyPlans)
            assertArrayEquals(f.originalBytes,f.originalFile.readBytes());assertArrayEquals(secondBytes,copy.readBytes())
        } finally {f.close()}
    }
    private suspend fun awaitReady(f:PreparationDeviceFixture) {
        withTimeout(20000){while(f.studyPreparation.state.value[f.pdf.id]?.busy!=false)delay(20)}
        assertNull(f.studyPreparation.state.value[f.pdf.id]?.error,f.studyPreparation.state.value[f.pdf.id]?.error)
    }
}
