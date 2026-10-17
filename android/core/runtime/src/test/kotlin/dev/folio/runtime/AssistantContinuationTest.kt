package dev.folio.runtime

import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.search.StudySearchEngine
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AssistantContinuationTest {
    @Test fun oneUserRequestSearchesAndOpensUsingFreshNativeIdentityRevisionAndPrivateRedaction() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0; val ids = mutableSetOf<String>()
        val before = f.workspace.state.value.activities
        val session = f.session({ request ->
            assertTrue(ids.add(request.requestId))
            assertFalse(request.capabilityContext.toString().contains("Private continuation draft"))
            if (calls++ == 0) f.search(request) else {
                assertEquals(2, request.sourceContext.getValue("continuation").jsonObject.getValue("round").jsonPrimitive.int)
                assertEquals(1L, request.capabilityContext.getValue("revision").jsonPrimitive.long)
                assertTrue(request.executionResults.single().getValue("accepted").jsonPrimitive.boolean)
                f.openResult(request)
            }
        })
        session.ask("Find Cell and open my saved note")
        assertEquals(2, calls); assertEquals(AssistantStatus.COMPLETED, session.state.value.status)
        assertEquals(AppDestination.NOTES, f.workspace.state.value.navigation.destination); assertEquals(before, f.workspace.state.value.activities)
        assertEquals(listOf("search.query", "search.open"), session.state.value.acceptedActions)
        assertEquals(1, session.state.value.messages.count { it.role == MessageRole.USER }); assertEquals(2, session.state.value.round)
    }
    @Test fun threeRoundLimitRetainsAcceptedStepsAndDoesNotKeepCallingProvider() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val session = f.session({ calls++; f.response(it, AppCommand.Navigate(AppDestination.HOME), true) })
        session.ask("Fixture bounded steps")
        assertEquals(3, calls); assertEquals(3L, f.workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, session.state.value.status); assertTrue(session.state.value.error!!.contains("three-step"))
        assertEquals(3, session.state.value.acceptedActions.size); assertNotNull(session.state.value.retryUtterance)
    }
    @Test fun safePartialFailureResumesFromFreshResultsWithoutRepeatingFirstStepEvenAfterHistoryRestore() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val first = f.session({ if (calls++ == 0) f.search(it) else throw ProviderApiException(AiProvider.GEMINI, 503) })
        first.ask("Find Cell and open my note"); assertEquals(AssistantStatus.FAILED, first.state.value.status)
        assertEquals(1L, f.workspace.state.value.revision); assertTrue(first.state.value.error!!.contains("Earlier app actions"))
        val recovered = f.session({ request ->
            assertTrue(request.sourceContext.getValue("continuation").jsonObject.getValue("resumeAfterFailure").jsonPrimitive.boolean)
            assertEquals(listOf("search.query"), request.sourceContext.getValue("continuation").jsonObject.getValue("priorAcceptedActions").jsonArray.map { it.jsonPrimitive.content })
            calls++; f.openResult(request)
        })
        recovered.restore(); assertEquals(2, calls); recovered.retry()
        assertEquals(3, calls); assertEquals(2L, f.workspace.state.value.revision)
        assertEquals(AssistantStatus.COMPLETED, recovered.state.value.status); assertEquals(AppDestination.NOTES, f.workspace.state.value.navigation.destination)
    }
    @Test fun learnerMutationsDoNotOfferReplayRetryAfterPartialFailure() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val before = f.workspace.state.value.activities.getValue("read").data as dev.folio.model.activity.ActivityData.Reader
        val session = f.session({ if (calls++ == 0) f.response(it, AppCommand.CreateAnnotation("read", "new-note", TestFixture.source,
            kind = dev.folio.model.source.AnnotationKind.NOTE, text = "Explicit requested saved note"), true) else throw ProviderApiException(AiProvider.GEMINI, 503) })
        session.ask("Save this requested note then continue")
        assertEquals(AssistantStatus.FAILED, session.state.value.status); assertNull(session.state.value.retryUtterance); assertFalse(session.state.value.retrySafe)
        session.retry(); assertEquals(2, calls)
        val after = f.workspace.state.value.activities.getValue("read").data as dev.folio.model.activity.ActivityData.Reader
        assertEquals(before.annotationDrafts, after.annotationDrafts); assertTrue("new-note" in after.annotations)
    }
    @Test fun relativePageMoveIsNotReplayedAfterLaterFailure() = runBlocking {
        val f = AssistantContinuationFixture()
        f.pdfEntries.value = listOf(dev.folio.model.library.LibraryPdf("pdf", "cell", TestFixture.catalog.documents().first(), 1))
        f.workspace.dispatchApp(AppCommandBatch(requestId = "open-fixture", commands = listOf(AppCommand.OpenPdf("pdf"))), TestFixture.user)
        var calls = 0
        val session = f.session({ if (calls++ == 0) f.response(it, AppCommand.NextPdfPage, true) else throw ProviderApiException(AiProvider.GEMINI, 503) })
        session.ask("Go to the next file page then continue")
        assertEquals(1, f.workspace.state.value.navigation.pdfPageIndex); assertEquals(AssistantStatus.FAILED, session.state.value.status)
        assertNull(session.state.value.retryUtterance); session.retry(); assertEquals(2, calls)
        assertEquals(1, f.workspace.state.value.navigation.pdfPageIndex)
    }
    @Test fun changedViewStopsBeforeProviderAndWritingLockRejectsTheNextMutation() = runBlocking {
        for (lock in listOf(false, true)) {
            val f = AssistantContinuationFixture(); var calls = 0; var waits = 0
            val session = f.session({ calls++; f.search(it) }, ready = {
                if (waits++ == 1) {
                    if (lock) f.locked = true else f.workspace.dispatchApp(AppCommandBatch(requestId = "touch",
                        commands = listOf(AppCommand.Navigate(AppDestination.SETTINGS))), TestFixture.user)
                }
                f.snapshot = StudySearchEngine(f.curriculum, TestFixture.catalog).search(f.workspace.state.value, f.library.entries.value)
            })
            session.ask("Find Cell and open it")
            assertEquals(if (lock) 2 else 1, calls); assertEquals(AssistantStatus.REJECTED, session.state.value.status)
            if (!lock) assertEquals(AppDestination.SETTINGS, f.workspace.state.value.navigation.destination)
            assertEquals(listOf("search.query"), session.state.value.acceptedActions)
        }
    }
    @Test fun protectedWritingStillAllowsAPlainReplyWithoutAnAppMutation() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0; var waits = 0
        val session = f.session({ request -> if (calls++ == 0) f.search(request) else MentorResponse(requestId = request.requestId,
            message = "Labelled plain reply without changing the workspace", batch = null) }, ready = {
                if (waits++ == 1) f.locked = true
                f.snapshot = StudySearchEngine(f.curriculum, TestFixture.catalog).search(it, f.library.entries.value)
            })
        session.ask("Search and describe without moving my protected writing")
        assertEquals(2, calls); assertEquals(AssistantStatus.COMPLETED, session.state.value.status)
        assertEquals(1L, f.workspace.state.value.revision)
    }
    @Test fun platformEffectsStopContinuationWithoutFakingExternalCompletion() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val session = f.session({ calls++; f.response(it, AppCommand.RequestPdfImport("cell"), true) })
        session.ask("Add a PDF and study it")
        assertEquals(1, calls); assertEquals(listOf(AppEffect.RequestPdfImport("cell")), f.effects)
        assertEquals(AssistantStatus.WAITING, session.state.value.status); assertFalse(session.state.value.retrySafe)
        assertTrue(session.state.value.messages.any { it.role == MessageRole.SYSTEM && it.text.contains("needs to finish") })
    }
    @Test fun nullBatchCannotRequestMoreCallsAndStaleSecondRoundNeverApplies() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val session = f.session({ request -> calls++; if (calls == 1) f.search(request) else f.openResult(request).let {
            it.copy(batch = it.batch!!.copy(expectedRevision = 0))
        } })
        session.ask("Search then open")
        assertEquals(AssistantStatus.FAILED, session.state.value.status); assertEquals(1L, f.workspace.state.value.revision)
        val empty = f.session({ request -> MentorResponse(requestId = request.requestId, message = "Invalid fixture", batch = null, continueAfterExecution = true) })
        empty.ask("Fixture"); assertEquals(AssistantStatus.FAILED, empty.state.value.status); assertEquals(1L, f.workspace.state.value.revision)
    }
    @Test fun wholeTurnDeadlineAndCancellationPreserveEarlierAcceptedStep() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val session = f.session({ if (calls++ == 0) f.search(it) else { delay(3000); f.openResult(it) } }, timeout = 500)
        session.ask("Fixture slow compound request")
        assertEquals(AssistantStatus.FAILED, session.state.value.status); assertEquals(1L, f.workspace.state.value.revision)
        assertTrue(session.state.value.error!!.contains("time limit"))
        val ready = CompletableDeferred<Unit>()
        val cancelled = f.session({ ready.complete(Unit); awaitCancellation() })
        val job = launch { cancelled.ask("Fixture cancellation") }; ready.await(); job.cancelAndJoin()
        assertEquals(AssistantStatus.CANCELLED, cancelled.state.value.status); assertEquals(1L, f.workspace.state.value.revision)
    }
    @Test fun safeRetryAfterManualNavigationRequiresNewIntentInsteadOfReplayingOldScope() = runBlocking {
        val f = AssistantContinuationFixture(); var calls = 0
        val session = f.session({ if (calls++ == 0) f.search(it) else throw ProviderApiException(AiProvider.GEMINI, 503) })
        session.ask("Find Cell and open it")
        f.workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(AppCommand.Navigate(AppDestination.SETTINGS))), TestFixture.user)
        session.retry(); assertEquals(2, calls); assertEquals(AssistantStatus.REJECTED, session.state.value.status)
        assertEquals(AppDestination.SETTINGS, f.workspace.state.value.navigation.destination)
        assertEquals(listOf("search.query"), session.state.value.acceptedActions)
    }
    @Test fun acknowledgementStorageFailureStopsBeforeNextCallAndDisablesRetry() = runBlocking {
        val f = AssistantContinuationFixture(); f.history.failAcknowledgement = true; var calls = 0
        val session = f.session({ calls++; f.search(it) }); session.ask("Find and open Cell")
        assertEquals(1, calls); assertEquals(1L, f.workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, session.state.value.status); assertNull(session.state.value.retryUtterance)
        assertTrue(session.state.value.error!!.contains("acknowledgement"))
    }
}
