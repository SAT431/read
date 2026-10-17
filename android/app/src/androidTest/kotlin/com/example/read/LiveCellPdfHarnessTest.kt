package com.example.read

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.assistant.StudyConversationDock
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.source.*
import dev.folio.model.study.*
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.study.*
import dev.folio.study.document.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** User-authorized screenshots/source calls, with the real Cell PDF copied into isolated private QA state. */
class LiveCellPdfHarnessTest {
    @get:Rule val compose=createAndroidComposeRule<AssistantTestActivity>()
    @Test fun actualCellTopicsOriginalCropsAndCrossPdfHarnessWorkWithoutChangingLearnerProgress()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive")=="true")
        val base=ApplicationProvider.getApplicationContext<Context>()
        val graph=(base.applicationContext as FolioApplication).graph
        graph.library.load()
        val chapters=graph.curriculum.chapters().filter { it.subject==SubjectId.BOTANY && it.title.contains("Cell",true) }
        val source=graph.library.entries.value.filter { pdf -> chapters.any{it.id==pdf.chapterId} }.maxByOrNull{it.importedAt}
            ?: error("Uploaded Cell PDF unavailable")
        graph.documentOutlines.load(source.document)
        val originalMap=requireNotNull(graph.documentOutlines.get(source.document.id))
        val f=PreparationDeviceFixture(base,CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate),live=true,sourcePdf=source)
        try {
            f.seed()
            val map=originalMap.copy(documentId=f.pdf.document.id)
            f.outlines.save(map)
            val figure=map.topics.firstOrNull{it.hasFigures && it.lastPage-it.firstPage<=1 && it.firstPage>0}
                ?: error("No bounded visual topic in the Cell map")
            val other=map.topics.firstOrNull{it.lastPage-it.firstPage<=1 && (it.lastPage<figure.firstPage || it.firstPage>figure.lastPage)}
                ?: error("No separate topic in the Cell map")
            val supplement=addSupplement(f)
            f.act(AppCommand.StudyPdf(f.pdf.id))
            val mentor=f.assistant(DirectGeminiMentor(requireNotNull(KeystoreProviderProfileStore(base).load()),
                listOf("gemini-orchestrator.md","native-tool-payloads.md").joinToString("\n") { base.assets.open(it).bufferedReader().use { r->r.readText() } }),documentStudy=true)
            showHarness(f,mentor)
            progress("Isolated Cell source and compact topic UI opened")
            compose.onNodeWithTag("study-chat-input").assertDoesNotExist()
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-topics.png")
            for(topic in listOf(other,figure)) {
                compose.onNodeWithTag("study-topic-list").performScrollToNode(hasTestTag("study-topic-${topic.id}"))
                compose.onNodeWithTag("study-topic-${topic.id}").performClick()
            }
            compose.onNodeWithTag("study-prepare").performClick()
            awaitPrepared(f)
            progress("Selected Cell topics prepared from the original PDF")
            val prepared=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(setOf(other.id,figure.id),prepared.topicIds.toSet())
            val visualPage=(figure.firstPage..figure.lastPage).firstOrNull { page -> f.catalog.page(f.pdf.document.id,page)?.elements?.any { it.kind==SourceElementKind.FIGURE }==true }
                ?: error("The Cell visual topic produced no original figure crop")
            f.act(AppCommand.StudyPage(f.pdf.id,prepared.version,visualPage))
            compose.waitUntil(10000){compose.onAllNodesWithTag("study-reading-list").fetchSemanticsNodes().isNotEmpty()}
            val visual=f.catalog.page(f.pdf.document.id,visualPage)!!.elements.first{it.kind==SourceElementKind.FIGURE}
            assertEquals(0f,requireNotNull(visual.mediaBounds).left)
            assertEquals(f.catalog.page(f.pdf.document.id,visualPage)!!.widthPoints,visual.mediaBounds!!.right)
            assertNotNull(f.media.read(requireNotNull(visual.mediaId)))
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("source-figure-${visual.id}"))
            compose.waitUntil(15000){compose.onAllNodesWithTag("source-figure-ready-${visual.id}").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("source-figure-ready-${visual.id}").assertIsDisplayed()
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-original-diagram.png")
            compose.onNodeWithTag("study-tools").performClick();compose.onNodeWithTag("study-sources").performClick()
            compose.onNodeWithTag("harness-source-${supplement.id}").performClick()
            compose.waitUntil(90000){f.indexing.state.value[supplement.document.id]?.let{!it.busy}==true}
            assertNull(f.indexing.state.value[supplement.document.id]?.error,f.indexing.state.value[supplement.document.id]?.error)
            progress("Second PDF mapped with native topic links")
            val secondaryMap=requireNotNull(f.outlines.get(supplement.document.id))
            for(topic in secondaryMap.topics) {
                compose.onNodeWithTag("harness-source-topics").performScrollToNode(hasTestTag("study-topic-${topic.id}"))
                compose.onNodeWithTag("study-topic-${topic.id}").performClick()
            }
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-source-picker.png")
            compose.onNodeWithTag("harness-add-topics").performClick();awaitPrepared(f)
            progress("Manual cross-PDF composition prepared")
            var plan=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            assertEquals(1,plan.attachedSources.size);assertEquals(visualPage,plan.pageIndex)
            assertEquals(StudyPlacement(f.pdf.id,visualPage),plan.attachedSources.single().placement)
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("study-inline-source-${supplement.id}-0"))
            compose.onNodeWithTag("study-inline-source-${supplement.id}-0").assertIsDisplayed()
            assertEquals(f.pdf.document.id,f.workspace.state.value.context.source!!.documentId)
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-inline-material.png")
            f.act(AppCommand.StudyPage(f.pdf.id,plan.version,0,supplement.id))
            compose.waitUntil(10000){f.workspace.state.value.context.source?.documentId==supplement.document.id}
            val supplementalFigure=f.catalog.page(supplement.document.id,0)!!.elements.first{it.kind==SourceElementKind.FIGURE}
            compose.onNodeWithTag("study-reading-list").performScrollToNode(hasTestTag("source-figure-${supplementalFigure.id}"))
            compose.waitUntil(15000){compose.onAllNodesWithTag("source-figure-ready-${supplementalFigure.id}").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("source-figure-ready-${supplementalFigure.id}").assertIsDisplayed()
            compose.waitForIdle();Thread.sleep(500);saveFixtureScreenshot("cell-harness-combined-reader.png")
            plan=f.workspace.state.value.studyPlans.getValue(f.pdf.id)
            f.act(AppCommand.HarnessRemoveSource(f.pdf.id,supplement.id,plan.version))
            progress("Requesting real Gemini source composition")
            mentor.ask("Add all topics from Cell tool supplement.pdf to my current study collection. Keep my current reading position and notes, and do not confirm reading. Use the harness's actual links and native preparation, then tell me when it is ready.")
            assertEquals(mentor.state.value.error,AssistantStatus.COMPLETED,mentor.state.value.status)
            progress("Gemini composition request completed")
            assertTrue("Gemini did not use the composition tool",mentor.state.value.acceptedActions.contains("harness.include_topics"))
            awaitPrepared(f)
            assertEquals(supplement.id,f.workspace.state.value.studyPlans.getValue(f.pdf.id).attachedSources.single().pdfId)
            assertTrue(f.workspace.state.value.activities.values.all{(it.data as? dev.folio.model.activity.ActivityData.Reader)?.readingConfirmations.isNullOrEmpty()})
            assertTrue(f.original.activities.all{(id,value)->f.workspace.state.value.activities[id]==value})
            assertArrayEquals(f.originalBytes,File(requireNotNull(source.document.localPdfPath)).readBytes())
            f.workspace.flush()
            assertEquals(f.workspace.state.value.studyPlans,f.repository.load()!!.studyPlans)
            progress("Original bytes, learner records and durable composition verified")
        } finally { f.close() }
    }
    private fun progress(message:String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(1,android.os.Bundle().apply{putString("stream","\nCell harness: $message\n")})
    }
    private suspend fun awaitPrepared(f: PreparationDeviceFixture) {
        withTimeout(150000) { while(f.studyPreparation.state.value[f.pdf.id]?.let{!it.busy}!=true) delay(100) }
        assertNull(f.studyPreparation.state.value[f.pdf.id]?.error,f.studyPreparation.state.value[f.pdf.id]?.error)
    }
    private fun showHarness(f: PreparationDeviceFixture,mentor: dev.folio.runtime.assistant.AssistantSession) {
        compose.setContent { FolioTheme(false,reducedMotion=true) {
            val state by f.workspace.state.collectAsState();val revision by f.pages.revision.collectAsState()
            val maps by f.outlines.outlines.collectAsState();val work by f.indexing.state.collectAsState()
            val assistant by mentor.state.collectAsState();val preparing by f.studyPreparation.state.collectAsState()
            val pdfs by f.library.entries.collectAsState()
            val plan=state.studyPlans[f.pdf.id];val outline=maps[f.pdf.document.id]
            val sources=remember(plan,maps,pdfs){plan?.let{StudySources.resolve(it,pdfs,f.outlines)}.orEmpty()}
            val ready=remember(sources,revision){StudySources.ready(sources,f.catalog)}
            var browser by remember{mutableStateOf(false)}
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Text("Cell PDF · isolated harness test; learner progress untouched")
                StudyHeader(f.pdf.document.title,plan?.mode==StudyMode.READING,sources.size,f.pdf.id,f.pdf.id,{browser=true}){runBlocking{f.act(it)}}
                Box(Modifier.weight(1f)) {
                    if(plan?.mode==StudyMode.READING && outline!=null && f.catalog.page(state.context.source?.documentId.orEmpty(),plan.pageIndex)!=null) SelectedTopicReader(plan,outline,f.catalog,f.media,20,revision,sources){runBlocking{f.act(it)}}
                    else if(plan?.mode==StudyMode.READING) Text(preparing[f.pdf.id]?.error ?: "Preparing selected text and original crops…")
                    else DocumentTopicsScreen(f.pdf.id,outline,plan,work[f.pdf.document.id]){runBlocking{f.act(it)}}
                }
                StudyConversationDock(f.pdf.id,assistant,VoiceInputState(),true,plan?.takeIf{it.mode==StudyMode.READING},ready,null,
                    {f.scope.launch{mentor.ask(it)};true},{}){runBlocking{f.act(it)}}
            }
            if(browser) StudySourcesSheet(f.pdf,pdfs,maps,work,plan,{browser=false}){runBlocking{f.act(it)}}
        } }
    }
    private suspend fun addSupplement(f: PreparationDeviceFixture): LibraryPdf {
        val file=File(f.originalFile.parentFile,"supplement.pdf")
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(600,800,1).create())
            val paint=Paint().apply{color=Color.BLACK;textSize=18f}
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Cell tool supplement",40f,70f,paint)
            page.canvas.drawText("Membranes separate cellular compartments.",40f,120f,paint)
            page.canvas.drawRect(80f,240f,320f,440f,Paint().apply{color=Color.GREEN})
            page.canvas.drawText("Figure 1. Controlled green rectangle.",40f,480f,paint)
            pdf.finishPage(page);file.outputStream().use(pdf::writeTo)
        } finally {pdf.close()}
        val hash=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
        return LibraryPdf("supplement-pdf",f.pdf.chapterId,SourceDocument("supplement-document","Cell tool supplement.pdf",1,hash,file.path),2).also{f.library.add(it)}
    }
}
