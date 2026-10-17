package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.assistant.AssistantPreparationWait
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AssistantPreparationTest {
    @Test fun preparationCompletesBeforeNextModelContextAndUsesOneNativeJob() = runBlocking {
        val f = PreparationFixture()
        try {
            val assistant = f.session(); val request = launch { assistant.ask("Prepare this page and explain it") }
            f.started.await(); assertTrue(f.stored.isEmpty()); assertEquals(1, f.mentorCalls.get())
            withTimeout(2000) { while (assistant.state.value.status != AssistantStatus.PREPARING) delay(5) }
            f.release.complete(Unit); request.join()
            assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status); assertEquals(2, f.mentorCalls.get())
            assertEquals(1, f.engineCalls.get()); assertTrue("explanation" in f.workspace.state.value.activities)
            assertEquals((f.original.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts,
                (f.workspace.state.value.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts)
        } finally { f.close() }
    }
    @Test fun changedPdfPageStopsFollowUpImmediatelyWhileOriginalJobCanStillCache() = runBlocking {
        val f = PreparationFixture()
        try {
            val assistant = f.session(); val request = launch { assistant.ask("Prepare and explain") }; val engineering = f.started.await()
            f.workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(AppCommand.PdfPage(1))), TestFixture.user)
            withTimeout(2000) { request.join() }
            assertEquals(AssistantStatus.REJECTED, assistant.state.value.status); assertEquals(1, f.mentorCalls.get())
            f.release.complete(Unit); f.jobs.await(engineering)
            assertEquals(1, f.workspace.state.value.navigation.pdfPageIndex); assertFalse("explanation" in f.workspace.state.value.activities)
            assertEquals(1, f.stored.size)
        } finally { f.close() }
    }
    @Test fun cancelledAssistantDoesNotReopenOrExplainAfterBackgroundCacheCompletes() = runBlocking {
        val f = PreparationFixture()
        try {
            val assistant = f.session(); val request = launch { assistant.ask("Prepare and explain") }; val engineering = f.started.await()
            withTimeout(2000) { while (assistant.state.value.status != AssistantStatus.PREPARING) delay(5) }
            request.cancelAndJoin(); assertEquals(AssistantStatus.CANCELLED, assistant.state.value.status)
            f.release.complete(Unit); f.jobs.await(engineering)
            assertEquals(1, f.mentorCalls.get()); assertFalse("explanation" in f.workspace.state.value.activities)
            assertEquals(f.original.navigation, f.workspace.state.value.navigation)
        } finally { f.close() }
    }
    @Test fun failedPreparationNeverRequestsExplanationOrAutomaticallyRetriesJob() = runBlocking {
        val f = PreparationFixture()
        try {
            f.failure = ProviderApiException(AiProvider.GEMINI, 503); f.release.complete(Unit)
            val assistant = f.session(); assistant.ask("Prepare and explain")
            assertEquals(AssistantStatus.FAILED, assistant.state.value.status); assertEquals(1, f.mentorCalls.get())
            assertTrue(f.stored.isEmpty()); assertNull(assistant.state.value.retryUtterance)
            assistant.retry(); assertEquals(1, f.engineCalls.get()); assertFalse("explanation" in f.workspace.state.value.activities)
        } finally { f.close() }
    }
    @Test fun viewportAndClockUpdatesAreCompatibleButTargetsAndPrivateInputChangesAreNot() {
        val state = TestFixture.reader()
        val reader = state.activities.getValue("read").data as ActivityData.Reader
        val moved = state.copy(revision = 99, preferences = state.preferences.copy(readingSizeSp = 24),
            activities = state.activities + ("read" to state.activities.getValue("read").copy(data = reader.copy(scrollIndex = 3, scrollOffset = 20))))
        assertTrue(AssistantPreparationWait.sameStudy(state, moved))
        assertFalse(AssistantPreparationWait.sameStudy(state, moved.copy(navigation = moved.navigation.copy(pdfPageIndex = 1))))
        val edited = moved.copy(activities = moved.activities + ("read" to moved.activities.getValue("read").copy(data = reader.copy(notes = mapOf("p" to "New private input")))))
        assertFalse(AssistantPreparationWait.sameStudy(state, edited))
    }
    @Test fun deadlineStopsWaitingButDoesNotReplayOrDiscardSharedPreparation() = runBlocking {
        val f = PreparationFixture()
        try {
            val assistant = f.session(timeout = 500); assistant.ask("Prepare and explain")
            assertEquals(AssistantStatus.FAILED, assistant.state.value.status); assertEquals(1, f.mentorCalls.get())
            assertNull(assistant.state.value.retryUtterance); assertFalse("explanation" in f.workspace.state.value.activities)
            val engineering = f.started.await(); f.release.complete(Unit); f.jobs.await(engineering)
            assertEquals(1, f.stored.size); assertEquals(1, f.mentorCalls.get())
        } finally { f.close() }
    }
    @Test fun awaiterRejectsUnknownPdfAndMixedPlatformEffectsBeforeStartingWork() = runBlocking {
        val f = PreparationFixture()
        try {
            val awaiter = dev.folio.runtime.engineering.PagePreparationAwaiter(f.jobs, f.catalog)
            val invalid = AppEffect.EngineerPage(f.request().copy(documentId = "unknown"))
            assertTrue(runCatching { awaiter.await(listOf(invalid)) }.exceptionOrNull() is dev.folio.runtime.engineering.PagePreparationException)
            assertEquals(0, f.engineCalls.get())
            assertFalse(awaiter.supports(listOf(AppEffect.EngineerPage(f.request()), AppEffect.RequestPdfImport("cell"))))
        } finally { f.close() }
    }
}
