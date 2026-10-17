package dev.folio.runtime.state

import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Orders semantic snapshots and same-revision clock checkpoints without persisting stale queued work. */
class SnapshotWriter(scope: CoroutineScope, private val repository: WorkspaceRepository, private val onError: (String?) -> Unit) {
    private data class Pending(val state: Workspace, val order: Long)
    private val sequence = AtomicLong()
    private val queue = Channel<Pending>(Channel.CONFLATED)
    private val writes = Mutex()
    private var savedRevision = -1L
    private var savedOrder = -1L
    private var savedState: Workspace? = null
    init { scope.launch { for (pending in queue) persist(pending) } }
    fun enqueue(state: Workspace) { queue.trySend(Pending(state, sequence.getAndIncrement())) }
    suspend fun flush(state: Workspace) = persist(Pending(state, sequence.getAndIncrement()))
    private suspend fun persist(pending: Pending) = writes.withLock {
        val state = pending.state
        if (state.revision < savedRevision || (state.revision == savedRevision && pending.order <= savedOrder)) return@withLock
        if (state == savedState) { savedOrder = maxOf(savedOrder, pending.order); return@withLock }
        try {
            repository.save(state); savedRevision = state.revision; savedOrder = pending.order; savedState = state; onError(null)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { onError("Save failed: ${error.message}") }
    }
}
