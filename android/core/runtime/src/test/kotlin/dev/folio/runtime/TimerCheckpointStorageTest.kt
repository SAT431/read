package dev.folio.runtime

import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.state.SnapshotWriter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TimerCheckpointStorageTest {
    private class Store : WorkspaceRepository {
        val writes = mutableListOf<Workspace>()
        var fail = false
        override suspend fun load() = writes.lastOrNull()
        override suspend fun save(workspace: Workspace) {
            if (fail) error("Controlled failure")
            writes.add(workspace)
        }
    }

    @Test fun newerSameRevisionCheckpointPersistsAndOlderQueueCannotOverwriteFlush() = runBlocking {
        val job = SupervisorJob()
        // Queue deliberately cannot run until this coroutine yields.
        val scope = CoroutineScope(coroutineContext + job)
        val store = Store()
        val writer = SnapshotWriter(scope, store) {}
        val first = TestFixture.reader().copy(revision = 10)
        val later = first.copy(focusedComponentId = null)
        writer.enqueue(first)
        writer.flush(later)
        yield()
        assertEquals(listOf(later), store.writes)
        writer.flush(first)
        assertEquals(listOf(later, first), store.writes)
        writer.flush(first.copy(revision = 9))
        assertEquals(2, store.writes.size)
        job.cancelAndJoin()
    }

    @Test fun failedCheckpointCanRetryAndIdenticalDurableStateIsNotWrittenTwice() = runBlocking {
        val job = SupervisorJob()
        val store = Store()
        var error: String? = null
        val writer = SnapshotWriter(CoroutineScope(coroutineContext + job), store) { error = it }
        val state = TestFixture.reader()
        store.fail = true
        writer.flush(state)
        assertNotNull(error)
        assertTrue(store.writes.isEmpty())
        store.fail = false
        writer.flush(state)
        assertNull(error)
        writer.flush(state)
        assertEquals(listOf(state), store.writes)
        job.cancelAndJoin()
    }
}
