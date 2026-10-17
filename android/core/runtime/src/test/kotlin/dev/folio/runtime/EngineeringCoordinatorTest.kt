package dev.folio.runtime

import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.runtime.engineering.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EngineeringCoordinatorTest {
    private val request = EngineeringRequest("job", "doc", 0, "hash", 1)
    private fun page() = SourcePage("doc", 0, "Page", listOf(SourceElement("p", SourceElementKind.PARAGRAPH, "Source", 0)))
    @Test fun concurrentRequestsUseOneProviderCallAndCache() = runBlocking {
        val cache = mutableMapOf<PageCacheKey, SourcePage>()
        val repository = object : PageRepository {
            override suspend fun find(key: PageCacheKey) = cache[key]
            override suspend fun save(key: PageCacheKey, page: SourcePage) { cache[key] = page }
        }
        var calls = 0
        val provider = object : PageEngineer {
            override suspend fun engineer(request: EngineeringRequest): SourcePage { calls++; delay(10); return page() }
        }
        val coordinator = EngineeringCoordinator(provider, repository)
        val result = awaitAll(async { coordinator.ensure(request) }, async { coordinator.ensure(request.copy(jobId = "other")) })
        assertEquals(result[0], result[1]); assertEquals(1, calls)
        coordinator.ensure(request); assertEquals(1, calls)
    }
    @Test fun rejectsWrongPageAutoApprovalAndCyclicSourceParents() {
        val validator = PageModelValidator()
        assertTrue(runCatching { validator.validate(request, page().copy(pageIndex = 3)) }.isFailure)
        assertTrue(runCatching { validator.validate(request, page().copy(status = EngineeringStatus.REVIEWED)) }.isFailure)
        val cycle = page().copy(elements = listOf(
            SourceElement("a", SourceElementKind.PARAGRAPH, "A", 0, parentId = "b"),
            SourceElement("b", SourceElementKind.PARAGRAPH, "B", 1, parentId = "a")))
        assertTrue(runCatching { validator.validate(request, cycle) }.isFailure)
    }
}
