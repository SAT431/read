package dev.folio.runtime.study

import dev.folio.model.study.*
import dev.folio.model.source.*
import dev.folio.model.port.SourceCatalog
import dev.folio.runtime.reading.SentenceIndex
import java.security.MessageDigest
import kotlinx.serialization.json.Json

object DocumentStudyRules {
    fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    fun fingerprint(outline: DocumentOutline) = hash(Json.encodeToString(outline))
    fun validate(outline: DocumentOutline) {
        require(outline.schemaVersion == 1 && outline.documentId.isNotBlank() && outline.checksum.length in 1..128)
        require(outline.pageCount in 1..10000 && outline.indexedPages.distinct().size == outline.indexedPages.size)
        require(outline.indexedPages.all { it in 0 until outline.pageCount } && outline.topics.size <= 2000)
        require(outline.topics.map { it.id }.distinct().size == outline.topics.size)
        outline.topics.forEach { t -> require(t.id.length in 1..100 && t.title.length in 1..200 && t.summary.length <= 2000)
            require(t.subtopics.size <= 30 && t.subtopics.all { it.length in 1..200 })
            require(t.firstPage in 0 until outline.pageCount && t.lastPage in t.firstPage until outline.pageCount)
            require(t.top.isFinite() && t.bottom.isFinite() && t.top in 0f..999.99f && t.bottom in .01f..1000f)
            require(t.firstPage != t.lastPage || t.top < t.bottom)
            require((t.firstPage..t.lastPage).all { it in outline.indexedPages }) }
    }
    fun validatePlans(plans: Map<String, DocumentStudyPlan>) {
        require(plans.size <= 500)
        plans.forEach { (id, p) -> require(id == p.pdfId && id.isNotBlank() && p.checksum.length in 1..128)
            require(p.outlineFingerprint.length == 64 && p.topicIds.size <= 2000 && p.topicIds.distinct().size == p.topicIds.size)
            require(p.pages.size <= 10000 && p.pages == p.pages.distinct().sorted() && p.pages.all { it >= 0 })
            require(p.mode != StudyMode.READING || p.pages.isNotEmpty())
            require(p.attachedSources.size <= 50 && p.attachedSources.map { it.pdfId }.distinct().size == p.attachedSources.size)
            require(p.attachedSources.none { it.pdfId == p.pdfId })
            p.attachedSources.forEach { link ->
                require(link.pdfId.isNotBlank() && link.checksum.length in 1..128 && link.outlineFingerprint.length == 64)
                require(link.topicIds.isNotEmpty() && link.topicIds.size <= 2000 && link.topicIds.distinct().size == link.topicIds.size)
                require(link.pages.isNotEmpty() && link.pages.size <= 10000 && link.pages == link.pages.distinct().sorted() && link.pages.all { it >= 0 })
                var cursor=link;val visited=mutableSetOf(link.pdfId)
                while(cursor.placement!=null) {
                    val at=requireNotNull(cursor.placement)
                    require(visited.add(at.afterPdfId)){"Circular study placement"}
                    if(at.afterPdfId==p.pdfId){require(at.afterPageIndex in p.pages);break}
                    cursor=p.attachedSources.firstOrNull{it.pdfId==at.afterPdfId} ?: error("Study insertion source unavailable")
                    require(at.afterPageIndex in cursor.pages)
                }
            }
            val activePages = if (p.activeSourcePdfId == null || p.activeSourcePdfId == p.pdfId) p.pages
                else p.attachedSources.firstOrNull { it.pdfId == p.activeSourcePdfId }?.pages ?: error("Unknown collection source")
            require(p.version in 1..1_000_000 && p.preparationVersion in 0..p.version && (activePages.isEmpty() || p.pageIndex in activePages) && p.sentenceId?.length?.let { it in 1..200 } != false) }
    }
    fun topics(plan: DocumentStudyPlan, outline: DocumentOutline): List<DocumentTopic> {
        require(plan.checksum == outline.checksum && plan.outlineFingerprint == fingerprint(outline)) { "Topic map changed. Select topics again." }
        return outline.topics.filter { it.id in plan.topicIds }.also { require(it.size == plan.topicIds.size) }
    }
    fun elements(page: SourcePage, topics: List<DocumentTopic>): List<SourceElement> = page.elements.sortedBy { it.order }.filter { e ->
        topics.any { t -> page.pageIndex in t.firstPage..t.lastPage && (e.bounds?.let { b ->
            val height = page.heightPoints ?: return@let true
            val top = b.top / height * 1000f; val bottom = b.bottom / height * 1000f
            bottom > (if (page.pageIndex == t.firstPage) t.top else 0f) && top < (if (page.pageIndex == t.lastPage) t.bottom else 1000f)
        } ?: true) }
    }
    fun sentences(plan: DocumentStudyPlan, outline: DocumentOutline, catalog: SourceCatalog): List<SourceSentence> {
        val topics = topics(plan, outline)
        return plan.pages.flatMap { index -> catalog.page(outline.documentId, index)?.let { page ->
            val ids = elements(page, topics).map { it.id }.toSet()
            SentenceIndex().compile(page).filter { it.source.elementId in ids }
        }.orEmpty() }
    }
}
