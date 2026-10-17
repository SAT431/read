package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.ui.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.*
import dev.folio.runtime.state.LayoutUndo
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RevisionQueueTest {
    private var clock = 1_000L
    private val reducer = RevisionQueueReducer(TestFixture.catalog) { clock }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 1, 1))
    }
    private val pdf = LibraryPdf("pdf", "cell", TestFixture.catalog.documents().first(), 0)
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(pdf))
        override suspend fun load() {}
    }
    private val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
    private fun initial(): Workspace {
        val questions = listOf(Question("q0", "First question", listOf("A", "B", "C", "D"), 0, "Explanation", listOf(TestFixture.source)),
            Question("q1", "Second question", listOf("A", "B", "C", "D"), 1, "Explanation", listOf(TestFixture.source.copy(pageIndex = 1))))
        return TestFixture.execute(TestFixture.reader(), WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ,
            TestFixture.source, ActivityData.Quiz(questions, answers = mapOf("q0" to 2), submitted = setOf("q0"))),
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, TestFixture.source,
                ActivityData.Recall("Recall prompt", "Reference", listOf(TestFixture.source), response = "Private draft"))).state
    }
    private fun schedule(state: Workspace = initial(), activity: String = "quiz", question: String? = "q1", days: Int = 3,
        actor: CommandActor = CommandActor.USER) = reducer.apply(state, AppCommand.ScheduleRevision(activity, question, days), actor)
    private fun apply(state: Workspace, vararg commands: AppCommand, policy: ExecutionPolicy = TestFixture.user) = executor.execute(state,
        AppCommandBatch(requestId = "revision", expectedRevision = state.revision, commands = commands.toList()), policy)
    private fun item(state: Workspace) = state.revisionItems.values.single()

    @Test fun schedulingKeepsLearningStateAndUsesNativeTimeAndKnownSource() {
        val before = initial(); val after = schedule(before)
        assertEquals(before.activities, after.activities); assertEquals(before.context, after.context)
        assertEquals(before.components, after.components)
        assertEquals(clock + 3 * 86_400_000L, item(after).dueAtEpochMillis)
        assertEquals(TestFixture.source.copy(pageIndex = 1), item(after).source)
        assertEquals("Second question", item(after).prompt); assertFalse(item(after).done)
        assertTrue(item(after).completions.isEmpty()); assertFalse(item(after).scheduledByAssistant)
    }
    @Test fun completeAndRescheduleRetainExplicitReportsAndDeduplicateTarget() {
        val scheduled = schedule(actor = CommandActor.AI); val first = item(scheduled)
        clock = 2_000L
        val done = reducer.apply(scheduled, AppCommand.CompleteRevision(first.id, first.version), CommandActor.AI)
        assertTrue(item(done).done); assertEquals(listOf(RevisionCompletion(clock, true, first.source, first.prompt)), item(done).completions)
        val again = schedule(done, days = 1)
        assertEquals(1, again.revisionItems.size); assertFalse(item(again).done)
        assertEquals(first.createdAtEpochMillis, item(again).createdAtEpochMillis)
        assertEquals(3, item(again).version); assertEquals(item(done).completions, item(again).completions)
        assertFalse(item(again).scheduledByAssistant)
    }
    @Test fun reschedulingChangedTargetKeepsTheOriginalCompletionEvidence() {
        val scheduled = schedule(); val original = item(scheduled)
        val done = reducer.apply(scheduled, AppCommand.CompleteRevision(original.id, 1), CommandActor.USER)
        val activity = done.activities.getValue("quiz"); val quiz = activity.data as ActivityData.Quiz
        val questions = quiz.questions.map { if (it.id == "q1") it.copy(prompt = "Updated question", sources = listOf(TestFixture.source)) else it }
        val changed = done.copy(activities = done.activities + (activity.id to activity.copy(data = quiz.copy(questions = questions))))
        val updated = schedule(changed)
        assertEquals("Updated question", item(updated).prompt)
        assertEquals(original.prompt, item(updated).completions.single().prompt)
        assertEquals(original.source, item(updated).completions.single().source)
    }
    @Test fun staleAndRepeatedCompletionOrRemovalAreRejected() {
        val scheduled = schedule(); val current = item(scheduled)
        val done = reducer.apply(scheduled, AppCommand.CompleteRevision(current.id, 1), CommandActor.USER)
        for (command in listOf(AppCommand.CompleteRevision(current.id, 1), AppCommand.CompleteRevision(current.id, 2),
            AppCommand.RemoveRevision(current.id, 1))) assertTrue(runCatching { reducer.apply(done, command, CommandActor.USER) }.isFailure)
        val removed = reducer.apply(done, AppCommand.RemoveRevision(current.id, 2), CommandActor.USER)
        assertTrue(removed.revisionItems.isEmpty()); assertEquals(done.activities, removed.activities)
    }
    @Test fun invalidTimingTargetsAndClockOverflowDoNotCreateReminders() {
        for ((activity, question, days) in listOf(Triple("quiz", "q1", -1), Triple("quiz", "q1", 366),
            Triple("quiz", null, 1), Triple("quiz", "unknown", 1), Triple("read", null, 1), Triple("recall", "q1", 1), Triple("missing", null, 1)))
            assertTrue(runCatching { schedule(activity = activity, question = question, days = days) }.isFailure)
        clock = Long.MAX_VALUE
        assertTrue(runCatching { schedule() }.isFailure)
        clock = -1
        assertTrue(runCatching { schedule(days = 0) }.isFailure)
        assertNotEquals(RevisionTargets.id("a", null), RevisionTargets.id("a", "recall"))
        assertNotEquals(RevisionTargets.id("a:b", "c"), RevisionTargets.id("a", "b:c"))
    }
    @Test fun openingSuspendedCapsuleQuizRestoresExactQuestionWithoutResettingAnswersOrReader() {
        val scheduled = schedule()
        val paused = TestFixture.execute(scheduled, WorkspaceCommand.SetPresentation("quiz-view", Presentation.CAPSULE),
            WorkspaceCommand.SuspendActivity("quiz")).state
        val result = apply(paused, AppCommand.OpenRevision(item(scheduled).id))
        assertTrue(result.trace.error, result.trace.accepted)
        val data = result.state.activities.getValue("quiz").data as ActivityData.Quiz
        assertEquals(1, data.index); assertEquals(mapOf("q0" to 2), data.answers); assertEquals(setOf("q0"), data.submitted)
        assertEquals(TestFixture.source.copy(pageIndex = 1), result.state.context.source)
        assertEquals(scheduled.activities.getValue("read"), result.state.activities.getValue("read"))
        assertEquals(Presentation.SURFACE, result.state.components.getValue("quiz-view").presentation)
        assertEquals(AppDestination.WORKSPACE, result.state.navigation.destination)
        assertEquals("pdf", result.state.navigation.pdfId); assertEquals("cell", result.state.navigation.chapterId)
        assertEquals(1, result.state.navigation.pdfPageIndex); assertEquals(scheduled.revisionItems, result.state.revisionItems)
    }
    @Test fun openingRecallRetainsUnrevealedPrivateResponseAndDoesNotMarkComplete() {
        val scheduled = schedule(activity = "recall", question = null)
        val paused = TestFixture.execute(scheduled, WorkspaceCommand.SuspendActivity("recall")).state
        val opened = apply(paused, AppCommand.OpenRevision(item(scheduled).id))
        assertTrue(opened.trace.error, opened.trace.accepted)
        assertEquals(scheduled.activities.getValue("recall").data, opened.state.activities.getValue("recall").data)
        assertEquals(scheduled.revisionItems, opened.state.revisionItems)
    }
    @Test fun changedOrUnavailableTargetCannotOpenButItsHistoryCanBeRecoveredAndRemoved() {
        val scheduled = schedule()
        val activity = scheduled.activities.getValue("quiz")
        val quiz = activity.data as ActivityData.Quiz
        val changed = scheduled.copy(activities = scheduled.activities + (activity.id to activity.copy(data = quiz.copy(
            questions = quiz.questions.map { if (it.id == "q1") it.copy(prompt = "Changed") else it }))))
        assertFalse(apply(changed, AppCommand.OpenRevision(item(scheduled).id)).trace.accepted)
        val orphaned = Workspace(revisionItems = scheduled.revisionItems)
        WorkspaceValidator(TestFixture.registry, TestFixture.catalog).validate(orphaned)
        assertFalse(apply(orphaned, AppCommand.OpenRevision(item(scheduled).id)).trace.accepted)
        assertTrue(apply(orphaned, AppCommand.RemoveRevision(item(scheduled).id, 1)).trace.accepted)
    }
    @Test fun revisionMutationRollsBackAlongWithOtherCommandsAndEffectsOnFailure() {
        val before = initial()
        val result = apply(before, AppCommand.ScheduleRevision("recall", null, 0), AppCommand.RequestPdfImport("cell"), AppCommand.OpenRevision("missing"))
        assertFalse(result.trace.accepted); assertEquals(before, result.state); assertTrue(result.effects.isEmpty())
    }
    @Test fun assistantExpansionRespectsPinAndInteractionProtection() {
        val scheduled = schedule()
        val pinned = scheduled.copy(components = scheduled.components + ("quiz-view" to scheduled.components.getValue("quiz-view").copy(
            presentation = Presentation.CAPSULE, pinned = true, visible = false)))
        val rejected = apply(pinned, AppCommand.OpenRevision(item(scheduled).id), policy = TestFixture.ai)
        assertFalse(rejected.trace.accepted); assertEquals(pinned, rejected.state)
        assertTrue(apply(pinned, AppCommand.OpenRevision(item(scheduled).id)).trace.accepted)
        val hiddenSurface = pinned.copy(components = pinned.components + ("quiz-view" to pinned.components.getValue("quiz-view").copy(presentation = Presentation.SURFACE)))
        assertFalse(apply(hiddenSurface, AppCommand.OpenRevision(item(scheduled).id), policy = TestFixture.ai).trace.accepted)
        val locked = apply(scheduled, AppCommand.CompleteRevision(item(scheduled).id, 1), policy = ExecutionPolicy(CommandActor.AI, interactionLocked = true))
        assertFalse(locked.trace.accepted); assertEquals(scheduled, locked.state)
    }
    @Test fun boundsRejectMalformedOrExcessiveHistoryAndVersionOverflow() {
        val scheduled = schedule(); val original = item(scheduled)
        for (bad in listOf(original.copy(version = 0), original.copy(done = true), original.copy(dueAtEpochMillis = -1),
            original.copy(id = "wrong"), original.copy(completions = List(101) { RevisionCompletion(0) })))
            assertTrue(runCatching { RevisionQueueValidator.validate(mapOf(bad.id to bad)) }.isFailure)
        val full = scheduled.copy(revisionItems = mapOf(original.id to original.copy(version = Int.MAX_VALUE)))
        assertTrue(runCatching { schedule(full) }.isFailure)
        assertTrue(runCatching { reducer.apply(full, AppCommand.CompleteRevision(original.id, Int.MAX_VALUE), CommandActor.USER) }.isFailure)
    }
    @Test fun wireContextAndLocalQueueNavigationExposeNativeCapabilitiesWithoutPrivateResponse() {
        val state = schedule(activity = "recall", question = null)
        val commands = listOf(AppCommand.ScheduleRevision("recall", null, 7), AppCommand.OpenRevision(item(state).id),
            AppCommand.CompleteRevision(item(state).id, 1), AppCommand.RemoveRevision(item(state).id, 1), AppCommand.Navigate(AppDestination.REVISION))
        val batch = AppCommandBatch(requestId = "revision", commands = commands)
        assertEquals(batch, AppCommandCodec().decode(AppCommandCodec().encode(batch)))
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(state, library.entries.value)).jsonObject
        assertEquals(1, context.getValue("revisionItems").jsonObject.size)
        assertTrue(context.getValue("nowEpochMillis").jsonPrimitive.long > 0)
        assertFalse(context.toString().contains("Private draft"))
        assertEquals(commands.first(), LearningCommandBridge.command(LearningCommandBridge.event(commands.first())!!))
        val open = dev.folio.runtime.assistant.LocalNavigationCommands(curriculum).resolve("Open revision queue", state, emptyList())!!
        assertEquals(listOf(AppCommand.Navigate(AppDestination.REVISION)), open.commands)
        assertTrue(apply(state, *open.commands.toTypedArray()).trace.accepted)
    }
    @Test fun layoutUndoPreservesRevisionScheduleAndReportedHistory() {
        val before = initial(); val undo = LayoutUndo().apply { record(before) }
        val scheduled = schedule(before)
        val done = reducer.apply(scheduled, AppCommand.CompleteRevision(item(scheduled).id, 1), CommandActor.USER)
        assertEquals(done.revisionItems, undo.restore(done)!!.revisionItems)
    }
}
