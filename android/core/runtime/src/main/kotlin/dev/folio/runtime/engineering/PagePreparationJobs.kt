package dev.folio.runtime.engineering

import dev.folio.model.port.*
import dev.folio.model.source.SourcePage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** App-owned jobs cache results only. Waiter cancellation never navigates or cancels another caller's job. */
data class PagePreparationStatus(val busy: Boolean, val message: String)
class PagePreparationException(message: String) : IllegalStateException(message)
class PagePreparationJobs(private val engineering: EngineeringCoordinator, private val scope: CoroutineScope) {
    private class Entry(val request: EngineeringRequest, val order: Long) {
        val callers = mutableSetOf(request.jobId)
        lateinit var task: Deferred<SourcePage>
        var status = PagePreparationStatus(true, "Queued page ${request.pageIndex + 1}…")
    }
    private val entries = LinkedHashMap<PageCacheKey, Entry>()
    private val slots = Semaphore(2)
    private var sequence = 0L
    private val mutableJobs = MutableStateFlow<Map<String, PagePreparationStatus>>(emptyMap())
    val jobs = mutableJobs.asStateFlow()
    fun start(request: EngineeringRequest): Deferred<SourcePage> {
        require(request.jobId.isNotBlank() && request.jobId.length <= 100 && request.documentId.isNotBlank() &&
            request.documentChecksum.isNotBlank() && request.pageIndex >= 0 && request.engineeringVersion > 0)
        val key = PageCacheKey(request.documentChecksum, request.documentId, request.pageIndex, request.engineeringVersion)
        val entry = synchronized(entries) {
            entries[key]?.let { old ->
                if (!old.task.isCompleted || request.jobId in old.callers) { old.callers.add(request.jobId); return old.task }
            }
            while (entries.size >= 100) {
                val old = entries.entries.firstOrNull { it.value.task.isCompleted }?.key ?: break
                entries.remove(old)
            }
            val next = Entry(request, ++sequence)
            if (entries.values.count { !it.task.isCompleted } >= 20) {
                val failure = PagePreparationException("Processing queue is full. Try again after a page completes.")
                next.task = CompletableDeferred<SourcePage>().apply { completeExceptionally(failure) }
                next.status = PagePreparationStatus(false, failure.message.orEmpty())
            } else next.task = scope.async(start = CoroutineStart.LAZY) {
                try {
                    val result = slots.withPermit {
                        update(next, PagePreparationStatus(true, "Processing page ${request.pageIndex + 1}…"))
                        engineering.ensure(request)
                    }
                    update(next, PagePreparationStatus(false, "Page cached for offline study. Reconstruction remains unchecked.")); result
                } catch (cancelled: CancellationException) {
                    update(next, PagePreparationStatus(false, "Page processing interrupted. Original PDF preserved.")); throw cancelled
                } catch (error: ProviderApiException) {
                    update(next, PagePreparationStatus(false, error.message.orEmpty())); throw error
                } catch (error: ProviderFailureException) {
                    update(next, PagePreparationStatus(false, error.message.orEmpty().replace("Your command was not applied", "The page was not cached"))); throw error
                } catch (_: Exception) {
                    val failure = PagePreparationException("Page processing failed. Original PDF and saved work are preserved.")
                    update(next, PagePreparationStatus(false, failure.message.orEmpty())); throw failure
                }
            }
            entries.remove(key); entries[key] = next; publish(); next
        }
        entry.task.start()
        return entry.task
    }
    suspend fun await(request: EngineeringRequest) = start(request).await()
    private fun update(entry: Entry, status: PagePreparationStatus) = synchronized(entries) { entry.status = status; publish() }
    private fun publish() {
        mutableJobs.value = entries.values.groupBy { key(it.request.documentId, it.request.pageIndex) }
            .mapValues { (_, versions) -> versions.maxBy { it.order }.status }
    }
    companion object { fun key(documentId: String, pageIndex: Int) = "$documentId:$pageIndex" }
}
