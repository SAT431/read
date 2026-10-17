package dev.folio.runtime.source

import dev.folio.model.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class SourceCacheRepairPhase { CLOSED, CHECKING, REVIEW, REPAIRING, DONE, ERROR }
data class SourceCacheRepairState(val phase: SourceCacheRepairPhase = SourceCacheRepairPhase.CLOSED,
    val preview: SourceCacheRepairPreview? = null, val confirmationToken: String? = null,
    val receipt: SourceCacheRepairReceipt? = null, val message: String? = null)

/** Application-owned, nonpersistent confirmation; commands only emit effects after atomic validation. */
class SourceCacheRepairSession(private val store: SourceCacheRepairStore) : SourceCacheRepairApproval {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SourceCacheRepairState())
    val state = mutableState.asStateFlow()
    override fun canConfirm(token: String) = token.isNotBlank() && state.value.let {
        it.phase == SourceCacheRepairPhase.REVIEW && it.confirmationToken == token && it.preview?.needsRepair == true
    }
    suspend fun request() = mutex.withLock {
        if (state.value.phase in setOf(SourceCacheRepairPhase.REVIEW, SourceCacheRepairPhase.REPAIRING)) return@withLock
        mutableState.value = SourceCacheRepairState(SourceCacheRepairPhase.CHECKING)
        try {
            val preview = store.previewRepair()
            mutableState.value = if (preview.needsRepair) SourceCacheRepairState(SourceCacheRepairPhase.REVIEW,
                preview, UUID.randomUUID().toString()) else SourceCacheRepairState(SourceCacheRepairPhase.DONE,
                preview, message = "No damaged cached pages or comparison reports were found.")
        } catch (cancelled: CancellationException) {
            mutableState.value = SourceCacheRepairState(); throw cancelled
        } catch (_: Exception) { failed("The cache could not be checked. Stored data is preserved. Try checking again.") }
    }
    suspend fun confirm(token: String) = mutex.withLock {
        if (!canConfirm(token)) return@withLock
        val preview = state.value.preview!!
        mutableState.value = SourceCacheRepairState(SourceCacheRepairPhase.REPAIRING, preview)
        // Once the learner confirms, rotation/background cancellation cannot interrupt backup + commit.
        withContext(NonCancellable) {
            try {
                val receipt = store.repair(preview.fingerprint)
                mutableState.value = SourceCacheRepairState(SourceCacheRepairPhase.DONE, preview, receipt = receipt)
            } catch (_: Exception) { failed("Repair did not finish. Check the cache again before retrying. Original PDFs and study records are unchanged.") }
        }
    }
    suspend fun dismiss() = mutex.withLock {
        if (state.value.phase != SourceCacheRepairPhase.REPAIRING) mutableState.value = SourceCacheRepairState()
    }
    private fun failed(message: String) { mutableState.value = SourceCacheRepairState(SourceCacheRepairPhase.ERROR, message = message) }
}
