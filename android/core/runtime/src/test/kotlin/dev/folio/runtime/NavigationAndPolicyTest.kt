package dev.folio.runtime

import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.GestureId
import dev.folio.model.ui.MotionId
import dev.folio.model.workspace.InteractionLock
import dev.folio.runtime.gesture.GestureRegistry
import dev.folio.runtime.motion.MotionRegistry
import org.junit.Assert.*
import org.junit.Test

class NavigationAndPolicyTest {
    @Test fun focusingDifferentSourceElementClearsUnrelatedSentenceSelection() {
        val initial = TestFixture.reader()
        val activity = initial.activities.getValue("read")
        val before = initial.copy(activities = initial.activities + (activity.id to activity.copy(
            data = dev.folio.model.activity.ActivityData.Reader(selectedSentence = "previous-sentence"))))
        val changed = TestFixture.execute(before, WorkspaceCommand.FocusSource(TestFixture.source.copy(elementId = "figure")))
        assertTrue(changed.trace.accepted)
        assertNull((changed.state.activities.getValue("read").data as dev.folio.model.activity.ActivityData.Reader).selectedSentence)
    }
    @Test fun pageCommandsRespectChapterBoundaries() {
        val opened = TestFixture.execute(TestFixture.reader(), WorkspaceCommand.OpenChapter("chapter")).state
        assertEquals(1, opened.context.source!!.pageIndex)
        val next = TestFixture.execute(opened, WorkspaceCommand.NextPage)
        assertTrue(next.trace.accepted); assertEquals(2, next.state.context.source!!.pageIndex)
        assertFalse(TestFixture.execute(next.state, WorkspaceCommand.NextPage).trace.accepted)
        assertFalse(TestFixture.execute(opened, WorkspaceCommand.PreviousPage).trace.accepted)
    }
    @Test fun noChapterRequiresKnownDocumentAndValidPage() {
        assertFalse(TestFixture.execute(TestFixture.reader(), WorkspaceCommand.OpenChapter("missing")).trace.accepted)
        assertFalse(TestFixture.execute(TestFixture.reader(), WorkspaceCommand.OpenPage("doc", 999)).trace.accepted)
    }
    @Test fun nativeSelectionAndWritingWinGestureConflicts() {
        assertFalse(GestureRegistry.allowed(GestureId.TAP, InteractionLock.TEXT_SELECTION))
        assertFalse(GestureRegistry.allowed(GestureId.TRANSFORM, InteractionLock.WRITING))
        assertTrue(GestureRegistry.allowed(GestureId.SCROLL, InteractionLock.NONE))
    }
    @Test fun reducedMotionDisablesEveryRegisteredAnimation() {
        MotionId.entries.forEach { assertEquals(0, MotionRegistry.resolve(it, true).durationMs) }
    }
}
