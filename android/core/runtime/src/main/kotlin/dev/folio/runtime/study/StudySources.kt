package dev.folio.runtime.study

import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.study.*

data class StudySource(val pdf: LibraryPdf, val outline: DocumentOutline, val plan: DocumentStudyPlan, val placement: StudyPlacement? = null)
data class StudyPageReference(val source: StudySource, val pageIndex: Int)

/** A composition holds exact references, never a second copy or a rewrite of an original PDF. */
object StudySources {
    fun resolve(plan: DocumentStudyPlan, pdfs: List<LibraryPdf>, outlines: DocumentOutlineRepository): List<StudySource> =
        (listOf(StudySourceSelection(plan.pdfId,plan.checksum,plan.outlineFingerprint,plan.topicIds,plan.pages)) + plan.attachedSources).map { link ->
            val pdf = pdfs.firstOrNull { it.id == link.pdfId } ?: error("A study source is unavailable. Remove its reference or restore the PDF.")
            require(pdf.document.checksum == link.checksum) { "Study source changed. Select its topics again." }
            val outline = outlines.get(pdf.document.id) ?: error("Open the source's topic map first")
            DocumentStudyRules.validate(outline)
            val selected = plan.copy(pdfId=link.pdfId,checksum=link.checksum,outlineFingerprint=link.outlineFingerprint,
                topicIds=link.topicIds,pages=link.pages,attachedSources=emptyList(),activeSourcePdfId=null)
            val topics = DocumentStudyRules.topics(selected,outline)
            require(topics.flatMap { (it.firstPage..it.lastPage).toList() }.distinct().sorted() == link.pages) { "Selected source pages changed" }
            StudySource(pdf,outline,selected,link.placement)
        }
    fun pages(sources: List<StudySource>) = StudyComposition.pages(sources)
    fun sentences(sources: List<StudySource>, catalog: SourceCatalog) = pages(sources).flatMap { reference ->
        catalog.page(reference.source.pdf.document.id,reference.pageIndex)?.let { page ->
            val topics=DocumentStudyRules.topics(reference.source.plan,reference.source.outline)
            val ids=DocumentStudyRules.elements(page,topics).map{it.id}.toSet()
            dev.folio.runtime.reading.SentenceIndex().compile(page).filter{it.source.elementId in ids}
        }.orEmpty()
    }
    fun ready(sources: List<StudySource>, catalog: SourceCatalog) = pages(sources).let { pages -> pages.isNotEmpty() && pages.all {
        (catalog.page(it.source.pdf.document.id,it.pageIndex)?.engineeringVersion ?: 0) >= PageEngineeringVersion.CURRENT
    } }
    fun active(plan: DocumentStudyPlan, sources: List<StudySource>) = sources.first { it.pdf.id == (plan.activeSourcePdfId ?: plan.pdfId) }
}
