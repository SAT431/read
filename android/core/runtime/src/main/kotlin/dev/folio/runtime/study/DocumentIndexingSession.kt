package dev.folio.runtime.study

import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-owned, resumable chunk indexing. Completion never changes the user's navigation. */
class DocumentIndexingSession(val repository: DocumentOutlineRepository, private val chunks: DocumentChunkSource,
    private val mapper: suspend () -> DocumentMapper, private val scope: CoroutineScope) {
    private val tasks = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String,LinkedHashSet<String>>()
    private val mutable = MutableStateFlow<Map<String, DocumentStudyWork>>(emptyMap())
    val state = mutable.asStateFlow()
    private val slot = Mutex()
    @Synchronized fun ensure(document: SourceDocument, retry: Boolean = false, requestId: String? = null) {
        if(requestId!=null) {
            require(requestId.isNotBlank() && requestId.length<=200)
            val seen=requests.getOrPut(document.id){linkedSetOf()}
            if(!seen.add(requestId)) return
            while(seen.size>32) seen.remove(seen.first())
        }
        if ((tasks[document.id]?.isActive == true && mutable.value[document.id]?.busy == true) || (!retry && document.id in mutable.value)) return
        update(document.id, DocumentStudyWork(true, total = document.pageCount, message = "Opening saved topic map…"))
        tasks[document.id] = scope.launch {
            try {
                update(document.id, DocumentStudyWork(true, total = document.pageCount, message = "Opening saved topic map…"))
                slot.withLock {
                    repository.load(document)
                    var saved = repository.get(document.id)?.takeIf { it.checksum == document.checksum }
                        ?: DocumentOutline(document.id, document.checksum, document.pageCount)
                    var batchLimit = 8
                    indexing@ while (saved.indexedPages.size < document.pageCount) {
                        currentCoroutineContext().ensureActive()
                        val first = (0 until document.pageCount).first { it !in saved.indexedPages }
                        val available = (first until minOf(first + batchLimit, document.pageCount)).takeWhile { it !in saved.indexedPages }.size
                        update(document.id, DocumentStudyWork(true, saved.indexedPages.size, document.pageCount,
                            "Mapping topics · ${saved.indexedPages.size} of ${document.pageCount} pages"))
                        val chunk = chunks.create(document, first, available)
                        try {
                            val proposed = try { mapper().map(chunk) } catch (error: ProviderFailureException) {
                                if (error.reason == ProviderFailureReason.INCOMPLETE_RESPONSE && chunk.pageCount > 1) {
                                    batchLimit = maxOf(1,chunk.pageCount / 2); continue@indexing
                                }
                                throw error
                            }
                            require(proposed.size in 1..100)
                            val covered = (chunk.firstPage until chunk.firstPage + chunk.pageCount).toList()
                            val topics = proposed.map { t ->
                                require(t.firstPage in covered && t.lastPage in t.firstPage..covered.last())
                                t.copy(id = "topic-" + DocumentStudyRules.hash("${document.checksum}:${chunk.firstPage}:${t.title}:${t.firstPage}:${t.lastPage}:${t.top}:${t.bottom}").take(40))
                            }
                            val missing = covered.filter { page -> topics.none { page in it.firstPage..it.lastPage } }.map { page ->
                                DocumentTopic("unmapped-$page", "Page ${page + 1} · inspect original", "No topic was identified for this page. Select it to inspect its content.", page, page)
                            }
                            val next = saved.copy(indexedPages = (saved.indexedPages + covered).distinct().sorted(), topics = saved.topics + topics + missing)
                            DocumentStudyRules.validate(next); repository.save(next); saved = next
                        } finally { withContext(NonCancellable) { chunks.release(chunk) } }
                    }
                    update(document.id, DocumentStudyWork(false, saved.indexedPages.size, document.pageCount, "Topic map ready · AI interpretation, linked to original pages"))
                }
            } catch (cancelled: CancellationException) {
                update(document.id, DocumentStudyWork(error = "Analysis paused. Completed page batches are saved; tap Resume analysis.")); throw cancelled
            } catch (error: DocumentMappingException) { update(document.id, DocumentStudyWork(error = error.message)) }
            catch (error: ProviderApiException) { update(document.id, DocumentStudyWork(error = error.message)) }
            catch (error: ProviderFailureException) { update(document.id, DocumentStudyWork(error = error.message)) }
            catch (_: Exception) { update(document.id, DocumentStudyWork(error = "Topic analysis could not finish. Original PDF and completed batches are preserved. Resume analysis to retry.")) }
        }
    }
    fun cancel(documentId: String) { tasks[documentId]?.cancel() }
    private fun update(id: String, value: DocumentStudyWork) { mutable.update { it + (id to value) } }
}
