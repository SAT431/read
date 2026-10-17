package dev.folio.runtime

import dev.folio.model.port.*
import dev.folio.runtime.engineering.PagePreparationException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PagePreparationJobsTest {
    @Test fun sameRequestAndConcurrentCallersShareOneJobAndCacheRepairCanPrepareAgain() = runBlocking {
        val f = PreparationFixture()
        try {
            val first = f.jobs.start(f.request("one")); withTimeout(2000) { f.started.await() }
            val same = f.jobs.start(f.request("one")); val joined = f.jobs.start(f.request("two"))
            assertSame(first, same); assertSame(first, joined); assertEquals(1, f.engineCalls.get())
            f.release.complete(Unit); first.await(); assertSame(first, f.jobs.start(f.request("two")))
            f.jobs.start(f.request("three")).await(); assertEquals(1, f.engineCalls.get())
            f.stored.clear(); f.jobs.start(f.request("after-explicit-repair")).await(); assertEquals(2, f.engineCalls.get())
            assertFalse(f.jobs.jobs.value.getValue("doc:0").busy)
        } finally { f.close() }
    }
    @Test fun failedSameRequestDoesNotRetryUntilExplicitNewJobIdentity() = runBlocking {
        val f = PreparationFixture()
        try {
            f.failure = ProviderApiException(AiProvider.GEMINI, 503); f.release.complete(Unit)
            assertTrue(runCatching { f.jobs.await(f.request("one")) }.exceptionOrNull() is ProviderApiException)
            assertTrue(runCatching { f.jobs.await(f.request("one")) }.exceptionOrNull() is ProviderApiException)
            assertEquals(1, f.engineCalls.get()); assertTrue(f.stored.isEmpty())
            f.failure = null; f.jobs.await(f.request("explicit-retry")); assertEquals(2, f.engineCalls.get())
        } finally { f.close() }
    }
    @Test fun queueIsBoundedAndWorkerSlotsNeverExceedTwo() = runBlocking {
        val f = PreparationFixture()
        try {
            val tasks = (0 until 20).map { f.jobs.start(f.request("queue-$it", it)) }
            withTimeout(2000) { while (f.engineCalls.get() < 2) delay(5) }
            assertEquals(2, f.engineCalls.get())
            val rejected = f.jobs.start(f.request("overflow", 20))
            assertTrue(runCatching { rejected.await() }.exceptionOrNull() is PagePreparationException)
            f.release.complete(Unit); tasks.awaitAll(); assertEquals(20, f.engineCalls.get()); assertEquals(2, f.peak.get())
            assertTrue(runCatching { f.jobs.await(f.request("overflow", 20)) }.isFailure)
            assertEquals(20, f.engineCalls.get())
            f.jobs.await(f.request("new-after-room-in-queue", 20)); assertEquals(21, f.engineCalls.get())
        } finally { f.close() }
    }
    @Test fun cancellingOneWaiterDoesNotCancelSharedJobOrInventAReadyPage() = runBlocking {
        val f = PreparationFixture()
        try {
            val task = f.jobs.start(f.request()); f.started.await()
            val waiter = launch { f.jobs.await(f.request()) }; waiter.cancelAndJoin()
            assertFalse(task.isCancelled); assertTrue(f.stored.isEmpty())
            f.release.complete(Unit); task.await(); assertEquals(1, f.stored.size); assertEquals(1, f.engineCalls.get())
        } finally { f.close() }
    }
}
