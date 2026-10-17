package dev.folio.runtime

import dev.folio.model.command.AppEffect
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.study.DocumentTopic
import dev.folio.runtime.engineering.*
import dev.folio.runtime.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PdfHarnessAwaiterTest {
    private class Fixture {
        val f=PdfHarnessFixture().apply{start()}
        val state=MutableStateFlow(f.state)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val entered=CompletableDeferred<Unit>();var gate:CompletableDeferred<Unit>?=null;var failure=false
        var mapped=0
        val index=DocumentIndexingSession(f.outlines,object:DocumentChunkSource{
            override suspend fun create(document:SourceDocument,firstPage:Int,count:Int)=DocumentChunk("fixture",firstPage,count)
            override suspend fun release(chunk:DocumentChunk)=Unit
        },mapper={object:DocumentMapper{override suspend fun map(chunk:DocumentChunk):List<DocumentTopic>{
            mapped++;entered.complete(Unit);gate?.await()
            if(failure) throw ProviderApiException(AiProvider.GEMINI,429)
            return listOf(DocumentTopic("","Controlled","Fixture",chunk.firstPage,chunk.firstPage+chunk.pageCount-1))
        }}},scope=scope)
        val jobs=PagePreparationJobs(EngineeringCoordinator(object:PageEngineer{
            override suspend fun engineer(request:EngineeringRequest):SourcePage=error("Cache should be reused")
        },object:PageRepository{
            override suspend fun find(key:PageCacheKey)=f.catalog.page(key.documentId,key.pageIndex)
            override suspend fun save(key:PageCacheKey,page:SourcePage)=Unit
        }),scope)
        val preparing=StudyPreparationSession(f.library,f.outlines,jobs,f.catalog,state,scope)
        val awaiter=PdfHarnessAwaiter(PagePreparationAwaiter(jobs,f.catalog),index,preparing,f.library,f.outlines,state,f.catalog)
        suspend fun close(){scope.coroutineContext.job.cancelAndJoin()}
    }
    @Test fun mappingCompletionDoesNotClaimExtractedReadingSourceOrChangeFocus()=runBlocking{
        val x=Fixture()
        try{
            x.f.maps.value=x.f.maps.value-"second-doc"
            val before=x.state.value
            val result=withTimeout(3000){x.awaiter.await(listOf(AppEffect.AnalyzeStudy("second")))}.single()
            assertEquals("topic_mapping",result.kind);assertFalse(result.sourcePageAvailable)
            assertEquals(0,result.engineeringVersion);assertEquals(before,x.state.value);assertEquals(1,x.mapped)
            assertEquals(3,x.f.outlines.get("second-doc")!!.indexedPages.size)
        }finally{x.close()}
    }
    @Test fun collectionPreparationWaitsForAllLinkedOriginalPagesAndReusesNativeCache()=runBlocking{
        val x=Fixture()
        try{
            x.f.include("second-a","second-c");x.state.value=x.f.state
            val result=withTimeout(3000){x.awaiter.await(listOf(AppEffect.PrepareStudy("first",x.f.plan.preparationVersion)))}
            assertEquals(listOf("first-doc:0","second-doc:0","second-doc:2"),result.map{"${it.source.documentId}:${it.source.pageIndex}"})
            assertTrue(result.all{it.sourcePageAvailable && it.engineeringVersion==PageEngineeringVersion.CURRENT})
            assertEquals(3,x.preparing.state.value["first"]!!.completed);assertEquals(x.f.state,x.state.value)
        }finally{x.close()}
    }
    @Test fun failedMappingNeverProducesSuccessfulCompletionMetadata()=runBlocking{
        val x=Fixture()
        try{
            x.f.maps.value=x.f.maps.value-"second-doc";x.failure=true
            assertTrue(runCatching{withTimeout(3000){x.awaiter.await(listOf(AppEffect.AnalyzeStudy("second")))}}.isFailure)
            assertNull(x.f.outlines.get("second-doc"));assertNotNull(x.index.state.value["second-doc"]!!.error)
        }finally{x.close()}
    }
    @Test fun cancellingAssistantWaitLeavesAppOwnedMappingAbleToCacheWithoutNavigation()=runBlocking{
        val x=Fixture()
        try{
            x.f.maps.value=x.f.maps.value-"second-doc";x.gate=CompletableDeferred()
            val wait=async{x.awaiter.await(listOf(AppEffect.AnalyzeStudy("second")))}
            withTimeout(3000){x.entered.await()};wait.cancelAndJoin()
            assertTrue(x.index.state.value["second-doc"]!!.busy)
            x.gate!!.complete(Unit)
            withTimeout(3000){while(x.index.state.value["second-doc"]?.busy!=false)delay(5)}
            assertNotNull(x.f.outlines.get("second-doc"));assertEquals(x.f.state,x.state.value)
        }finally{x.close()}
    }
}
