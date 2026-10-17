package dev.folio.runtime.engineering

import dev.folio.model.port.*
import dev.folio.model.source.SourcePage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Per-page work survives navigation conceptually: results are cached, never applied to UI here. */
class EngineeringCoordinator(private val provider: PageEngineer, private val pages: PageRepository,
    private val validator: PageModelValidator = PageModelValidator(),
    private val cachedUpgrade: suspend (EngineeringRequest,SourcePage)->SourcePage = {_,page->page}) {
    private val locks = ConcurrentHashMap<PageCacheKey, Mutex>()
    suspend fun ensure(request: EngineeringRequest): SourcePage {
        require(request.pageIndex >= 0 && request.documentChecksum.isNotBlank())
        val key = PageCacheKey(request.documentChecksum, request.documentId, request.pageIndex, request.engineeringVersion)
        return locks.getOrPut(key) { Mutex() }.withLock {
            pages.find(key)?.let { saved ->
                val upgraded=cachedUpgrade(request,saved)
                if(upgraded!=saved) {
                    require(upgraded.copy(elements=upgraded.elements.mapIndexed { index,element ->
                        val previous=saved.elements[index]
                        element.copy(mediaId=previous.mediaId,mediaBounds=previous.mediaBounds)
                    })==saved) { "Asset upgrade cannot rewrite source content" }
                    validator.validate(request,upgraded.copy(status=dev.folio.model.source.EngineeringStatus.UNCHECKED,corrections=emptyMap()))
                    pages.save(key,upgraded)
                }
                return@withLock upgraded
            }
            val result = provider.engineer(request)
            validator.validate(request, result)
            pages.save(key, result)
            result
        }
    }
}
