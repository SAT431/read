package dev.folio.runtime

import dev.folio.model.command.AppCommand
import dev.folio.model.study.*
import dev.folio.runtime.study.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StudyCompositionTest {
    private fun setup():PdfHarnessFixture=PdfHarnessFixture().apply{
        start();run(AppCommand.SelectStudyTopics("first",fingerprint(first),listOf("first-a","first-c")))
        run(AppCommand.BeginStudy("first",plan.version));include("second-b")
    }
    @Test fun addedMaterialIsInsertedAtCurrentReadingPageAndHasOriginalOwnership() {
        val f=setup();val sources=StudySources.resolve(f.plan,f.library.entries.value,f.outlines)
        assertEquals(StudyPlacement("first",0),f.plan.attachedSources.single().placement)
        assertEquals(listOf("first:0","second:1","first:2"),StudySources.pages(sources).map{"${it.source.pdf.id}:${it.pageIndex}"})
        val surface=StudyComposition.surface(f.plan,sources)
        assertEquals("first",surface.root.source.pdf.id);assertEquals(0,surface.root.pageIndex)
        assertEquals(listOf("first","second"),surface.sections.map{it.source.pdf.id})
        f.run(AppCommand.StudyPage("first",f.plan.version,1,"second"))
        val focused=StudyComposition.surface(f.plan,StudySources.resolve(f.plan,f.library.entries.value,f.outlines))
        assertEquals(surface.root.pdfKey(),focused.root.pdfKey())
        assertEquals("first",f.state.navigation.pdfId);assertEquals("second-doc",f.state.context.source!!.documentId)
    }
    @Test fun explicitGuideAdvancesThroughInsertedSourceThenReturnsToNextBasePage() {
        val f=setup()
        assertTrue(f.run(AppCommand.GuideStudy("first",f.plan.version,true)).trace.accepted)
        assertTrue(f.run(AppCommand.FinishStudyLine("first",f.plan.version,f.plan.sentenceId!!)).trace.accepted)
        assertEquals("second",f.plan.activeSourcePdfId);assertEquals(1,f.plan.pageIndex)
        assertTrue(f.run(AppCommand.FinishStudyLine("first",f.plan.version,f.plan.sentenceId!!)).trace.accepted)
        assertEquals("first",f.plan.activeSourcePdfId);assertEquals(2,f.plan.pageIndex)
        assertEquals(2,StudyComposition.surface(f.plan,StudySources.resolve(f.plan,f.library.entries.value,f.outlines)).root.pageIndex)
    }
    @Test fun changingBaseTopicsMovesUnavailableInsertionPointWithoutErasingSourceSelection() {
        val f=setup();val attached=f.plan.attachedSources.single()
        assertTrue(f.run(AppCommand.SelectStudyTopics("first",f.fingerprint(f.first),listOf("first-c"))).trace.accepted)
        assertEquals(attached.copy(placement=StudyPlacement("first",2)),f.plan.attachedSources.single())
        DocumentStudyRules.validatePlans(f.state.studyPlans)
        assertTrue(f.run(AppCommand.SelectStudyTopics("first",f.fingerprint(f.first),emptyList())).trace.accepted)
        assertNull(f.plan.attachedSources.single().placement);DocumentStudyRules.validatePlans(f.state.studyPlans)
    }
    @Test fun startingGuideUsesTheFocusedSourcePageBeforeEarlierUnreadMaterial() {
        val f=setup()
        f.run(AppCommand.StudyPage("first",f.plan.version,1,"second"))
        assertTrue(f.run(AppCommand.GuideStudy("first",f.plan.version,true)).trace.accepted)
        assertEquals("second",f.plan.activeSourcePdfId);assertEquals(1,f.plan.pageIndex)
        assertEquals("second-doc",f.state.context.source!!.documentId)
        assertTrue(f.state.activities.values.all{(it.data as? dev.folio.model.activity.ActivityData.Reader)?.readingConfirmations.isNullOrEmpty()})
        assertTrue(f.run(AppCommand.FinishStudyLine("first",f.plan.version,f.plan.sentenceId!!)).trace.accepted)
        val readers=f.state.activities.values.mapNotNull{it.data as? dev.folio.model.activity.ActivityData.Reader}
        assertEquals(listOf("second-doc"),readers.flatMap{it.readingConfirmations.values}.map{it.source.documentId})
    }
    @Test fun legacyUnplacedReferenceCanMergeWhileFocusedWithoutCreatingSelfCycle() {
        val f=setup();f.state=f.state.copy(studyPlans=mapOf("first" to f.plan.copy(attachedSources=f.plan.attachedSources.map{it.copy(placement=null)})))
        f.run(AppCommand.StudyPage("first",f.plan.version,1,"second"))
        assertTrue(f.include("second-a").trace.accepted)
        assertNull(f.plan.attachedSources.single().placement);DocumentStudyRules.validatePlans(f.state.studyPlans)
    }
    @Test fun malformedPlacementCannotCycleOrEscapeSelectedPagesAndSurvivesCodecWhenValid() {
        val f=setup()
        assertEquals(f.plan,Json.decodeFromString<DocumentStudyPlan>(Json.encodeToString(f.plan)))
        for(at in listOf(StudyPlacement("second",1),StudyPlacement("first",1),StudyPlacement("missing",0))) {
            val bad=f.plan.copy(attachedSources=listOf(f.plan.attachedSources.single().copy(placement=at)))
            assertTrue(runCatching{DocumentStudyRules.validatePlans(mapOf("first" to bad))}.isFailure)
        }
    }
    @Test fun removingParentReferenceReanchorsRetainedChildrenInsteadOfDeletingThem() {
        val f=setup();val firstLink=f.plan.attachedSources.single()
        val child=firstLink.copy(pdfId="third",checksum="third-hash",placement=StudyPlacement("second",1))
        f.state=f.state.copy(studyPlans=mapOf("first" to f.plan.copy(attachedSources=listOf(firstLink,child))))
        assertTrue(f.run(AppCommand.HarnessRemoveSource("first","second",f.plan.version)).trace.accepted)
        assertEquals(listOf(child.copy(placement=StudyPlacement("first",0))),f.plan.attachedSources)
        DocumentStudyRules.validatePlans(f.state.studyPlans)
    }
    @Test fun inlineSourceOriginalLinkOpensExactPageAndRetainsDestinationStudy() {
        val f=setup()
        assertTrue(f.run(AppCommand.HarnessInspectPage("second",1)).trace.accepted)
        assertEquals("second",f.state.navigation.pdfId);assertEquals(1,f.state.navigation.pdfPageIndex)
        f.run(AppCommand.Back);assertEquals("first",f.state.navigation.pdfId)
        val before=f.state;assertFalse(f.run(AppCommand.HarnessInspectPage("second",99)).trace.accepted);assertEquals(before,f.state)
    }
    private fun StudyPageReference.pdfKey()=source.pdf.id to pageIndex
}
