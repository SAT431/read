package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.study.*
import dev.folio.model.workspace.AppDestination
import dev.folio.runtime.study.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PdfHarnessTest {
    @Test fun randomTopicsFromAnotherPdfProduceLinkedPagesAndNativePreparationOnly() {
        val f=PdfHarnessFixture();f.start();val original=f.state.activities
        val result=f.include("second-c","second-a");assertTrue(result.trace.error,result.trace.accepted)
        assertEquals(listOf(0,2),f.plan.attachedSources.single().pages)
        assertEquals(listOf("second-c","second-a"),f.plan.attachedSources.single().topicIds)
        assertEquals(original,f.state.activities);assertEquals("first",f.state.navigation.pdfId)
        assertEquals(listOf(AppEffect.PrepareStudy("first",f.plan.preparationVersion)),result.effects)
        val sources=StudySources.resolve(f.plan,f.library.entries.value,f.outlines)
        assertEquals(listOf("first-doc:0","second-doc:0","second-doc:2"),StudySources.pages(sources).map{"${it.source.pdf.document.id}:${it.pageIndex}"})
    }
    @Test fun linkedPageNavigationAndGuidanceKeepConfirmationsWithTheirOriginalDocument() {
        val f=PdfHarnessFixture();f.start();assertTrue(f.include("second-b").trace.accepted)
        assertTrue(f.run(AppCommand.GuideStudy("first",f.plan.version,true)).trace.accepted)
        val firstSentence=f.plan.sentenceId!!
        assertTrue(f.run(AppCommand.FinishStudyLine("first",f.plan.version,firstSentence)).trace.accepted)
        assertEquals("second",f.plan.activeSourcePdfId);assertEquals("second-doc",f.state.context.source!!.documentId)
        assertEquals("first",f.state.navigation.pdfId)
        val secondSentence=f.plan.sentenceId!!
        assertNotEquals(firstSentence,secondSentence)
        assertTrue(f.run(AppCommand.FinishStudyLine("first",f.plan.version,secondSentence)).trace.accepted)
        assertFalse(f.plan.guided)
        for(doc in listOf("first-doc","second-doc")) {
            val reader=f.state.activities.getValue("reader-$doc").data as ActivityData.Reader
            assertEquals(doc,reader.readingConfirmations.values.single().source.documentId)
        }
    }
    @Test fun staleDestinationVersionWrongMapOrUnknownTopicCannotPartiallyAddSources() {
        val f=PdfHarnessFixture();f.start();val before=f.state
        for(command in listOf(
            AppCommand.HarnessIncludeTopics("first","second",f.plan.version-1,f.fingerprint(f.second),listOf("second-a")),
            AppCommand.HarnessIncludeTopics("first","second",f.plan.version,"0".repeat(64),listOf("second-a")),
            AppCommand.HarnessIncludeTopics("first","second",f.plan.version,f.fingerprint(f.second),listOf("foreign")))) {
            val result=f.run(command);assertFalse(result.trace.accepted);assertEquals(before,result.state);assertTrue(result.effects.isEmpty())
        }
    }
    @Test fun originalTopicLinksOpenExactPageAndBackReturnsToDestinationCollection() {
        val f=PdfHarnessFixture();f.start()
        assertTrue(f.run(AppCommand.HarnessInspectTopic("second",f.fingerprint(f.second),"second-c")).trace.accepted)
        assertEquals(AppDestination.PDF,f.state.navigation.destination);assertEquals(2,f.state.navigation.pdfPageIndex)
        assertEquals("second",f.state.navigation.pdfId)
        assertTrue(f.run(AppCommand.Back).trace.accepted)
        assertEquals(AppDestination.STUDY,f.state.navigation.destination);assertEquals("first",f.state.navigation.pdfId)
    }
    @Test fun removingReferenceRetainsCachedReadersAndSavedReportsAndCanRecoverMissingMap() {
        val f=PdfHarnessFixture();f.start();f.include("second-a")
        f.run(AppCommand.StudyPage("first",f.plan.version,0,"second"))
        val before=f.state.activities
        f.maps.value=f.maps.value-"second-doc"
        assertTrue(f.run(AppCommand.HarnessRemoveSource("first","second",f.plan.version)).trace.accepted)
        assertTrue(f.plan.attachedSources.isEmpty());assertEquals("first-doc",f.state.context.source!!.documentId)
        assertEquals(before.getValue("reader-second-doc"),f.state.activities.getValue("reader-second-doc"))
    }
    @Test fun repeatedAddMergesTopicLinksWithoutDuplicatingSourceOrPages() {
        val f=PdfHarnessFixture();f.start();f.include("second-a");f.include("second-a","second-c")
        assertEquals(1,f.plan.attachedSources.size);assertEquals(listOf(0,2),f.plan.attachedSources.single().pages)
    }
    @Test fun anyPdfCanBeAnalyzedAndTopicDiscoveryIsPagedWithoutChangingCurrentReading() {
        val f=PdfHarnessFixture();f.start();val before=f.state.context
        assertEquals(listOf(AppEffect.AnalyzeStudy("second","harness")),f.run(AppCommand.HarnessAnalyzePdf("second")).effects)
        f.run(AppCommand.HarnessBrowseTopics("second","beta"))
        val context=StudyAssistantContext.build(f.state,f.library,f.outlines)
        val browse=context.getValue("browse").jsonObject
        assertEquals("second-b",browse.getValue("topics").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(before,f.state.context);assertEquals("first",f.state.navigation.pdfId)
        assertFalse(context.toString().contains("/private/"))
    }
    @Test fun crossPdfSelectionCursorAndSourceLinksSurviveCodecAndRejectForgedPages() {
        val f=PdfHarnessFixture();f.start();f.include("second-b")
        f.run(AppCommand.StudyPage("first",f.plan.version,1,"second"))
        val recovered=Json.decodeFromString<dev.folio.model.workspace.Workspace>(Json.encodeToString(f.state))
        assertEquals(f.state,recovered);DocumentStudyRules.validatePlans(recovered.studyPlans)
        val broken=f.plan.copy(attachedSources=listOf(f.plan.attachedSources.single().copy(pages=listOf(2))))
        assertTrue(runCatching{StudySources.resolve(broken,f.library.entries.value,f.outlines)}.isFailure)
    }
    @Test fun reopeningCollectionRestoresAttachedPdfCursorWithoutNewReadingReport() {
        val f=PdfHarnessFixture();f.start();f.include("second-b");f.run(AppCommand.StudyPage("first",f.plan.version,1,"second"))
        val before=f.plan
        assertTrue(f.run(AppCommand.StudyPdf("first",resume=true)).trace.accepted)
        assertEquals("second-doc",f.state.context.source!!.documentId);assertEquals(1,f.state.context.source!!.pageIndex)
        assertEquals(before,f.plan)
        assertTrue(f.state.activities.values.all{(it.data as? ActivityData.Reader)?.readingConfirmations.isNullOrEmpty()})
    }
    @Test fun normalStudyEntryShowsTopicsFirstWithoutErasingPriorSourceSelectionOrReports() {
        val f=PdfHarnessFixture();f.start();f.include("second-b")
        val saved=f.plan;val records=f.state.activities
        assertTrue(f.run(AppCommand.StudyPdf("first")).trace.accepted)
        assertEquals(StudyMode.TOPICS,f.plan.mode);assertEquals(saved.attachedSources,f.plan.attachedSources)
        assertEquals(saved.topicIds,f.plan.topicIds)
        assertTrue(records.all{(id,value)->f.state.activities[id]?.data==value.data})
    }
}
