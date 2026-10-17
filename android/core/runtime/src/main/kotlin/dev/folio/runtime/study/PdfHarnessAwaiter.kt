package dev.folio.runtime.study

import dev.folio.model.command.AppEffect
import dev.folio.model.port.*
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import kotlinx.coroutines.flow.*

/** Joins app-owned native mapping/extraction. It never navigates, executes code or reports learner progress. */
class PdfHarnessAwaiter(private val engineering: AssistantEffectAwaiter, private val indexing: DocumentIndexingSession,
    private val preparing: StudyPreparationSession, private val library: PdfLibrary, private val outlines: DocumentOutlineRepository,
    private val workspace: StateFlow<Workspace>, private val catalog: SourceCatalog) : AssistantEffectAwaiter {
    override fun supports(effects: List<AppEffect>) = effects.isNotEmpty() && effects.all {
        it is AppEffect.AnalyzeStudy || it is AppEffect.PrepareStudy || engineering.supports(listOf(it))
    }
    override suspend fun await(effects: List<AppEffect>): List<PreparedSourceResult> {
        require(supports(effects))
        return effects.flatMap { effect -> when (effect) {
            is AppEffect.AnalyzeStudy -> {
                val pdf = library.entries.value.first { it.id == effect.pdfId }
                indexing.ensure(pdf.document,retry=true,requestId=effect.requestId.ifBlank{null})
                val work = indexing.state.map { it[pdf.document.id] }.filterNotNull().first { !it.busy }
                check(work.error == null) { work.error.orEmpty() }
                val map = outlines.get(pdf.document.id) ?: error("Topic map unavailable")
                require(map.checksum == pdf.document.checksum && map.indexedPages.size == pdf.document.pageCount)
                listOf(PreparedSourceResult("topic-map-${pdf.id}",SourceAnchor(pdf.document.id,0),0,"topic_mapping",false))
            }
            is AppEffect.PrepareStudy -> {
                preparing.ensure(effect.pdfId,effect.version)
                val work = preparing.state.map { it[effect.pdfId] }.filterNotNull().first { !it.busy }
                check(work.error == null) { work.error.orEmpty() }
                val plan = workspace.value.studyPlans.getValue(effect.pdfId)
                require(plan.preparationVersion == effect.version)
                val sources = StudySources.resolve(plan,library.entries.value,outlines)
                require(StudySources.ready(sources,catalog))
                StudySources.pages(sources).map { PreparedSourceResult("study-${effect.version}-${it.source.pdf.id}-${it.pageIndex}",
                    SourceAnchor(it.source.pdf.document.id,it.pageIndex),PageEngineeringVersion.CURRENT) }
            }
            else -> engineering.await(listOf(effect))
        } }
    }
}
