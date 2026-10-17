package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.source.*
import dev.folio.model.study.*
import dev.folio.model.workspace.*
import dev.folio.runtime.study.*
import dev.folio.runtime.assistant.LocalNavigationCommands
import org.junit.Assert.*
import org.junit.Test

class DocumentStudyTest {
    @Test fun studyOpensDedicatedFlowAndPreparesOnlySelectedPages() {
        val f = DocumentStudyFixture(); f.open(); assertEquals(AppDestination.STUDY, f.state.navigation.destination)
        f.select("b"); assertEquals(listOf(1,2), f.plan.pages)
        val result = f.run(AppCommand.BeginStudy("pdf", f.plan.version)); assertTrue(result.trace.error, result.trace.accepted)
        assertEquals(StudyMode.READING, f.plan.mode); assertEquals(1, f.state.context.source!!.pageIndex)
        assertEquals(AppEffect.PrepareStudy("pdf", f.plan.preparationVersion), result.effects.single())
    }
    @Test fun selectionCanClearAndCannotPrepareEmptyOrForgedTopics() {
        val f = DocumentStudyFixture(); f.open(); f.select("a"); f.select()
        assertFalse(f.run(AppCommand.BeginStudy("pdf", f.plan.version)).trace.accepted)
        val before = f.state
        assertFalse(f.run(AppCommand.SelectStudyTopics("pdf", f.plan.outlineFingerprint, listOf("fabricated"))).trace.accepted)
        assertEquals(before, f.state)
    }
    @Test fun guideRequiresExplicitExactCurrentConfirmationThenAdvancesAcrossSelectedPages() {
        val f = DocumentStudyFixture(); f.open(); f.select("b"); f.begin()
        assertTrue(f.run(AppCommand.GuideStudy("pdf", f.plan.version, true)).trace.accepted)
        val highlighted = f.plan.sentenceId!!; val version = f.plan.version
        assertEquals(1, f.plan.pageIndex)
        val before = (f.state.activities.getValue("reader-doc").data as ActivityData.Reader)
        assertTrue(before.readingConfirmations.isEmpty())
        assertTrue(f.run(AppCommand.FinishStudyLine("pdf", version, highlighted), CommandActor.AI).trace.accepted)
        assertEquals(2, f.plan.pageIndex)
        val reader = f.state.activities.getValue("reader-doc").data as ActivityData.Reader
        assertEquals(1, reader.readingConfirmations.size); assertTrue(reader.readingConfirmations.values.single().byAssistant)
        val saved = f.state
        assertFalse(f.run(AppCommand.FinishStudyLine("pdf", version, highlighted)).trace.accepted); assertEquals(saved, f.state)
        assertTrue(f.run(AppCommand.FinishStudyLine("pdf", f.plan.version, f.plan.sentenceId!!)).trace.accepted)
        assertFalse(f.plan.guided); assertNull(f.plan.sentenceId)
    }
    @Test fun changedStudyFocusRejectsOldYesAndVoiceCannotApplyToAChangedLine() {
        val f = DocumentStudyFixture(); f.open(); f.select("b"); f.begin(); f.run(AppCommand.GuideStudy("pdf", f.plan.version, true))
        val captured = GuidedStudyVoice.target(f.state)
        f.run(AppCommand.StudyPage("pdf", f.plan.version, 2))
        assertFalse(GuidedStudyVoice.maySubmit("yes", captured, f.state))
        assertTrue(GuidedStudyVoice.maySubmit("explain this", captured, f.state))
        val intent = LocalNavigationCommands(f.curriculum()).resolve("yes", f.state, listOf(f.pdf))!!
        assertEquals(AppCommand.FinishStudyLine("pdf", f.plan.version, f.plan.sentenceId!!), intent.commands.single())
        assertNull(LocalNavigationCommands(f.curriculum()).resolve("not yet", f.state, listOf(f.pdf)))
    }
    @Test fun staleMapVersionsAndWrongNativeSentenceAreRejectedAtomically() {
        val f = DocumentStudyFixture(); f.open(); f.select("a"); f.begin()
        val before = f.state
        assertFalse(f.run(AppCommand.SelectStudyTopics("pdf", "stale", listOf("b"))).trace.accepted)
        assertFalse(f.run(AppCommand.GuideStudy("pdf", f.plan.version - 1, true)).trace.accepted)
        assertFalse(f.run(AppCommand.StudySentence("pdf", f.plan.version, "fabricated")).trace.accepted)
        assertEquals(before, f.state)
    }
    @Test fun topicBoundsKeepSelectedOriginalFiguresAndExcludeOtherRegions() {
        val p = SourcePage("doc",0,"page", listOf(
            SourceElement("a",SourceElementKind.PARAGRAPH,"First.",0,SourceBounds(0f,0f,100f,30f)),
            SourceElement("fig",SourceElementKind.FIGURE,"Original",1,SourceBounds(0f,45f,100f,80f)),
            SourceElement("b",SourceElementKind.PARAGRAPH,"Second.",2,SourceBounds(0f,85f,100f,100f))), widthPoints=100f,heightPoints=100f)
        assertEquals(listOf("fig"), DocumentStudyRules.elements(p,listOf(DocumentTopic("x","Figure","",0,0,400f,810f))).map { it.id })
    }
    @Test fun persistedPlanValidationAndLegacyWorkspaceDefaultsPreserveRecovery() {
        val f = DocumentStudyFixture(); f.open(); f.select("b"); f.begin()
        DocumentStudyRules.validatePlans(f.state.studyPlans)
        assertTrue(runCatching { DocumentStudyRules.validatePlans(mapOf("pdf" to f.plan.copy(pages=listOf(2,1)))) }.isFailure)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        assertEquals(f.state, json.decodeFromString<Workspace>(json.encodeToString(f.state)))
        assertTrue(json.decodeFromString<Workspace>("{}").studyPlans.isEmpty())
    }
    @Test fun staleSourceMappingStillAllowsPausingAndReturningToTopicSelection() {
        val f=DocumentStudyFixture(); f.open(); f.select("a"); f.begin(); f.run(AppCommand.GuideStudy("pdf",f.plan.version,true))
        val old=f.outlines.get("doc")!!
        kotlinx.coroutines.runBlocking { f.outlines.save(old.copy(topics=old.topics.map { it.copy(title="Changed map") })) }
        assertTrue(f.run(AppCommand.GuideStudy("pdf",f.plan.version,false)).trace.accepted)
        assertTrue(f.run(AppCommand.ShowStudyTopics("pdf")).trace.accepted)
        assertEquals(StudyMode.TOPICS,f.plan.mode); assertFalse(f.plan.guided)
    }
    @Test fun reopeningStudyRestoresExactGuidedPageAndSentenceWithoutReportingAnythingRead() {
        val f=DocumentStudyFixture(); f.open(); f.select("b"); f.begin(); f.run(AppCommand.GuideStudy("pdf",f.plan.version,true))
        f.run(AppCommand.FinishStudyLine("pdf",f.plan.version,f.plan.sentenceId!!))
        val target=f.plan.sentenceId; val version=f.plan.version
        f.run(AppCommand.Navigate(AppDestination.HOME)); f.open(resume=true)
        val reader=f.state.activities.getValue("reader-doc").data as ActivityData.Reader
        assertEquals(2,f.state.context.source!!.pageIndex); assertEquals(target,reader.selectedSentence)
        assertEquals(version,f.plan.version); assertEquals(1,reader.readingConfirmations.size)
    }
}
