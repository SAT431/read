package dev.folio.runtime

import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.study.*
import dev.folio.runtime.study.DocumentIndexingSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class DocumentIndexingTest {
    @Test fun duplicateDispatchAndAwaitNeverRetryTheSameCompletedFailedAttempt() = runBlocking {
        val f=Fixture(1)
        try {
            f.invalid=true;f.session.ensure(f.document,retry=true,requestId="attempt-one");f.finished()
            assertEquals(1,f.calls.get());assertNull(f.repo.get("doc"))
            f.invalid=false;f.session.ensure(f.document,retry=true,requestId="attempt-one");delay(30)
            assertEquals(1,f.calls.get());assertNull(f.repo.get("doc"))
            f.session.ensure(f.document,retry=true,requestId="attempt-two");f.finished()
            assertEquals(2,f.calls.get());assertNotNull(f.repo.get("doc"))
        } finally {f.close()}
    }
    private class Fixture(val count: Int = 10) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger(); val releases = AtomicInteger()
        var failLast = false; var invalid = false; var truncate = false
        var gate: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        val document = SourceDocument("doc","Fixture",count,"checksum",null)
        val repo = object : DocumentOutlineRepository {
            override val outlines = MutableStateFlow<Map<String,DocumentOutline>>(emptyMap())
            override suspend fun load(document: SourceDocument) = Unit
            override suspend fun save(outline: DocumentOutline) { outlines.value = outlines.value + (outline.documentId to outline) }
        }
        val chunks = object : DocumentChunkSource {
            override suspend fun create(document: SourceDocument, firstPage: Int, count: Int) = DocumentChunk("fixture",firstPage,count)
            override suspend fun release(chunk: DocumentChunk) { releases.incrementAndGet() }
        }
        val mapper = object : DocumentMapper { override suspend fun map(chunk: DocumentChunk): List<DocumentTopic> {
            calls.incrementAndGet(); entered.complete(Unit); gate?.await()
            if (truncate && chunk.pageCount > 2) throw ProviderFailureException(AiProvider.GEMINI,ProviderFailureReason.INCOMPLETE_RESPONSE)
            if (failLast && chunk.firstPage >= 8) throw ProviderApiException(AiProvider.GEMINI,429)
            return listOf(DocumentTopic("ignored","Topic ${chunk.firstPage}","Fixture",chunk.firstPage,chunk.firstPage,
                top = if (invalid) -1f else 0f))
        } }
        val session = DocumentIndexingSession(repo,chunks,{mapper},scope)
        suspend fun finished() { withTimeout(3000) { while (session.state.value["doc"]?.busy != false) delay(5) } }
        suspend fun close() { scope.coroutineContext.job.cancelAndJoin() }
    }
    @Test fun completedBatchesPersistAndQuotaFailureResumesOnlyRemainingPages() = runBlocking {
        val f=Fixture()
        try {
            f.failLast=true; f.session.ensure(f.document); f.finished()
            assertEquals(8,f.repo.get("doc")!!.indexedPages.size); assertEquals(2,f.calls.get())
            assertNotNull(f.session.state.value["doc"]!!.error)
            f.failLast=false; f.session.ensure(f.document,retry=true); f.finished()
            assertEquals(10,f.repo.get("doc")!!.indexedPages.size); assertEquals(3,f.calls.get()); assertEquals(3,f.releases.get())
            f.session.ensure(f.document,retry=true); f.finished(); assertEquals(3,f.calls.get())
        } finally { f.close() }
    }
    @Test fun uncoveredPagesAreExplicitlyLabelledRatherThanSilentlyOmitted() = runBlocking {
        val f=Fixture(3)
        try { f.session.ensure(f.document); f.finished()
            val map=f.repo.get("doc")!!; assertEquals(listOf(0,1,2),map.indexedPages)
            assertEquals(2,map.topics.count { it.id.startsWith("unmapped-") }); assertTrue(map.topics.all { it.lastPage < 3 })
        } finally { f.close() }
    }
    @Test fun malformedRegionsNeverReplaceStoredSourceMapping() = runBlocking {
        val f=Fixture(1)
        try { f.invalid=true; f.session.ensure(f.document); f.finished()
            assertNull(f.repo.get("doc")); assertNotNull(f.session.state.value["doc"]!!.error); assertEquals(1,f.releases.get())
        } finally { f.close() }
    }
    @Test fun cancellationReleasesChunkAndNeverAutomaticallyRestartsNetworkWork() = runBlocking {
        val f=Fixture(1)
        try {
            f.gate=CompletableDeferred(); f.session.ensure(f.document); withTimeout(3000) { f.entered.await() }
            f.session.cancel("doc"); f.finished(); assertEquals(1,f.releases.get()); assertNull(f.repo.get("doc"))
            f.session.ensure(f.document); delay(50); assertEquals(1,f.calls.get())
            f.gate!!.complete(Unit); f.session.ensure(f.document,retry=true); f.finished(); assertNotNull(f.repo.get("doc"))
        } finally { f.close() }
    }
    @Test fun truncatedMappingShrinksBatchesWithoutAcceptingPartialJson() = runBlocking {
        val f=Fixture(4)
        try { f.truncate=true; f.session.ensure(f.document); f.finished()
            assertEquals(4,f.repo.get("doc")!!.indexedPages.size); assertEquals(3,f.calls.get()); assertEquals(3,f.releases.get())
        } finally { f.close() }
    }
}
