package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.RecallReducer
import dev.folio.runtime.validation.ActivityValidator
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class RecallTest {
    private val recall = ActivityData.Recall("What is recalled?", "Source reference answer", listOf(TestFixture.source))
    private fun initial(data: ActivityData.Recall = recall) = TestFixture.execute(TestFixture.reader(),
        WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, TestFixture.source, data)).state
    private fun data(state: Workspace) = state.activities.getValue("recall").data as ActivityData.Recall
    private val reducer = RecallReducer { 123456L }
    private fun apply(state: Workspace, event: LearningInteraction, actor: CommandActor = CommandActor.USER) = reducer.apply(state, event, actor)
    @Test fun responseIsLockedAfterRevealAndRatingRequiresExplicitReport() {
        val state = apply(initial(), LearningInteraction.RecallResponse("recall", "", "My recalled answer"))
        assertTrue(runCatching { apply(state, LearningInteraction.RateRecall("recall", RecallRating.RECALLED)) }.isFailure)
        val revealed = apply(state, LearningInteraction.RevealRecall("recall"))
        assertNull(data(revealed).rating)
        assertEquals("My recalled answer", data(revealed).response)
        assertTrue(runCatching { apply(revealed, LearningInteraction.RecallResponse("recall", "My recalled answer", "Changed")) }.isFailure)
        val rated = apply(revealed, LearningInteraction.RateRecall("recall", RecallRating.PARTIAL))
        assertEquals(123456L, data(rated).ratedAtEpochMillis)
        assertTrue(runCatching { apply(rated, LearningInteraction.RateRecall("recall", RecallRating.RECALLED)) }.isFailure)
    }
    @Test fun retryArchivesReportedResponseAndProvenanceWithoutOverwritingIt() {
        var state = apply(initial(), LearningInteraction.RecallResponse("recall", "", "Dictated answer"), CommandActor.AI)
        state = apply(state, LearningInteraction.RevealRecall("recall"))
        state = apply(state, LearningInteraction.RateRecall("recall", RecallRating.FORGOT), CommandActor.AI)
        state = apply(state, LearningInteraction.RestartRecall("recall"))
        assertEquals("", data(state).response); assertFalse(data(state).revealed); assertNull(data(state).rating)
        assertEquals(RecallAttempt("Dictated answer", RecallRating.FORGOT, 123456L, true, true), data(state).pastAttempts.single())
        assertFalse(data(state).responseByAssistant || data(state).ratingByAssistant)
        assertTrue(runCatching { apply(state, LearningInteraction.RestartRecall("recall")) }.isFailure)
    }
    @Test fun assistantContributionRemainsLabelledAfterEditsUntilResponseIsCleared() {
        val entered = apply(initial(), LearningInteraction.RecallResponse("recall", "", "Dictated"), CommandActor.AI)
        val edited = apply(entered, LearningInteraction.RecallResponse("recall", "Dictated", "Dictated and edited"))
        assertTrue(data(edited).responseByAssistant)
        val cleared = apply(edited, LearningInteraction.RecallResponse("recall", "Dictated and edited", ""))
        assertFalse(data(cleared).responseByAssistant)
    }
    @Test fun blankResponseMayBeRevealedWithoutInventingWrittenWorkOrMastery() {
        val revealed = apply(initial(), LearningInteraction.RevealRecall("recall"))
        assertEquals("", data(revealed).response); assertNull(data(revealed).rating)
        assertTrue(runCatching { apply(revealed, LearningInteraction.RevealRecall("recall")) }.isFailure)
    }
    @Test fun staleOversizedHiddenAndSuspendedEditsAreRejected() {
        val state = apply(initial(), LearningInteraction.RecallResponse("recall", "", "Current"))
        for (event in listOf(LearningInteraction.RecallResponse("recall", "", "Stale"),
            LearningInteraction.RecallResponse("recall", "Current", "x".repeat(4001))))
            assertTrue(runCatching { apply(state, event) }.isFailure)
        val paused = TestFixture.execute(state, WorkspaceCommand.SuspendActivity("recall")).state
        assertTrue(runCatching { apply(paused, LearningInteraction.RevealRecall("recall")) }.isFailure)
        val hidden = state.copy(components = state.components.mapValues { it.value.copy(visible = false) })
        assertTrue(runCatching { apply(hidden, LearningInteraction.RevealRecall("recall")) }.isFailure)
    }
    @Test fun aiCreationCannotInjectAnyLearnerHistoryOrState() {
        val command = WorkspaceCommand.CreateActivity("recall", "view", ComponentKind.RECALL, TestFixture.source, recall)
        assertTrue(TestFixture.execute(TestFixture.reader(), command, policy = TestFixture.ai).trace.accepted)
        for (injected in listOf(recall.copy(response = "Fabricated answer"), recall.copy(revealed = true),
            recall.copy(responseByAssistant = true), recall.copy(pastAttempts = listOf(RecallAttempt("Forged", RecallRating.RECALLED, 1L)))))
            assertFalse(TestFixture.execute(TestFixture.reader(), command.copy(data = injected), policy = TestFixture.ai).trace.accepted)
        assertTrue(runCatching { ActivityValidator.validate(recall.copy(rating = RecallRating.RECALLED), TestFixture.source, TestFixture.catalog) }.isFailure)
        assertTrue(runCatching { ActivityValidator.validate(recall.copy(sources = listOf(TestFixture.source.copy(documentId = "missing"))), TestFixture.source, TestFixture.catalog) }.isFailure)
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    @Test fun unfinishedResponseIsPrivateAndProtectsVisibleWorkspaceBeforeComposition() {
        val state = initial(recall.copy(response = "Private unfinished recall")).copy(navigation = AppNavigation(AppDestination.WORKSPACE))
        assertFalse(AppCapabilityContext(curriculum).encode(state, emptyList()).contains("Private unfinished recall"))
        assertTrue(InteractionGate().blocks(state))
        val revealed = apply(state, LearningInteraction.RevealRecall("recall"))
        assertTrue(AppCapabilityContext(curriculum).encode(revealed, emptyList()).contains("Private unfinished recall"))
        assertFalse(InteractionGate().blocks(revealed))
    }
    @Test fun wireBridgeAndAtomicAppExecutorCoverAllRecallActions() {
        val commands = listOf(AppCommand.RecallResponse("recall", "", "Answer"), AppCommand.RevealRecall("recall"),
            AppCommand.RateRecall("recall", RecallRating.RECALLED), AppCommand.RestartRecall("recall"))
        val batch = AppCommandBatch(requestId = "recall", commands = commands)
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        commands.forEach { assertEquals(it, LearningCommandBridge.command(requireNotNull(LearningCommandBridge.event(it)))) }
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
        val result = executor.execute(initial(), batch, TestFixture.user)
        assertTrue(result.trace.error, result.trace.accepted); assertEquals("Answer", data(result.state).pastAttempts.single().response)
        val invalid = executor.execute(initial(), batch.copy(commands = listOf(commands[0], commands[2])), TestFixture.user)
        assertFalse(invalid.trace.accepted); assertEquals(initial(), invalid.state)
    }
    @Test fun creationAlreadyShowsCardAndDuplicateShowRejectsWholeBatch() {
        val before = TestFixture.reader()
        val create = WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, TestFixture.source, recall)
        val created = TestFixture.execute(before, create)
        assertTrue(created.trace.accepted)
        assertTrue(created.state.components.getValue("recall-view").visible)
        assertEquals("recall-view", created.state.focusedComponentId)
        val duplicate = TestFixture.execute(before, create, WorkspaceCommand.ShowComponent("recall-view", "recall", ComponentKind.RECALL))
        assertFalse(duplicate.trace.accepted); assertEquals(before, duplicate.state)
    }
    @Test fun pauseSourceReviewResumeAndLayoutUndoRetainDraftWithoutSourceChanges() {
        val state = initial(recall.copy(response = "Unfinished answer"))
        val paused = TestFixture.execute(state, WorkspaceCommand.SuspendActivity("recall"), WorkspaceCommand.FocusSource(TestFixture.source.copy(pageIndex = 1))).state
        assertEquals("Unfinished answer", data(paused).response)
        val resumed = TestFixture.execute(paused, WorkspaceCommand.ResumeActivity("recall")).state
        assertEquals(TestFixture.source, resumed.context.source)
        assertEquals(data(state), data(resumed))
        val undo = dev.folio.runtime.state.LayoutUndo().apply { record(state) }
        assertEquals(data(resumed), data(requireNotNull(undo.restore(resumed))))
        assertTrue(state.sourceCorrections.isEmpty())
    }
}
