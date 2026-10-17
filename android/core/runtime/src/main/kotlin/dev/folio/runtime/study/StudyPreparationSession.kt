package dev.folio.runtime.study

import dev.folio.model.port.*
import dev.folio.model.study.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.engineering.PagePreparationJobs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StudyPreparationSession(private val library: PdfLibrary, private val outlines: DocumentOutlineRepository,
    private val pages: PagePreparationJobs, private val catalog: SourceCatalog, private val workspace: StateFlow<Workspace>,
    private val scope: CoroutineScope) {
    private val tasks = mutableMapOf<String, Job>()
    private val mutable = MutableStateFlow<Map<String, DocumentStudyWork>>(emptyMap())
    val state = mutable.asStateFlow()
    @Synchronized fun ensure(pdfId: String, version: Long) {
        val key = "$pdfId:$version"
        if (key in tasks) return
        tasks.filterKeys { it.startsWith("$pdfId:") && it != key }.values.forEach { it.cancel() }
        tasks.keys.filter { it.startsWith("$pdfId:") && it!=key }.forEach { tasks.remove(it) }
        update(pdfId,version,DocumentStudyWork(busy=true,message="Preparing linked source content…"))
        tasks[key] = scope.launch {
            try {
                val pdf = library.entries.value.first { it.id == pdfId }
                val plan = workspace.value.studyPlans[pdfId] ?: error("Selection unavailable")
                require(plan.preparationVersion == version && plan.mode == StudyMode.READING)
                val sources = StudySources.resolve(plan,library.entries.value,outlines)
                val references = StudySources.pages(sources)
                for ((i, reference) in references.withIndex()) {
                    val page = reference.pageIndex
                    val source = reference.source.pdf
                    currentCoroutineContext().ensureActive()
                    require(workspace.value.studyPlans[pdfId]?.preparationVersion == version) { "Selection changed" }
                    update(pdfId,version,DocumentStudyWork(true, i, references.size, "Preparing original text and diagrams · ${i + 1} of ${references.size} pages"))
                    pages.await(EngineeringRequest("study-${DocumentStudyRules.hash(key).take(32)}-${DocumentStudyRules.hash(source.id).take(16)}-$page", source.document.id, page,
                        source.document.checksum, PageEngineeringVersion.CURRENT))
                    require(catalog.page(source.document.id, page) != null)
                }
                update(pdfId,version,DocumentStudyWork(false, references.size, references.size, "Selected pages ready for reading"))
            } catch (cancelled: CancellationException) {
                update(pdfId,version,DocumentStudyWork(error = "Preparation paused. Cached pages are retained; a page already processing may still finish.")); throw cancelled
            } catch (error: ProviderApiException) { fail(pdfId,version,error.message) }
            catch (error: ProviderFailureException) { fail(pdfId,version,error.message) }
            catch (_: Exception) { fail(pdfId,version,"Selected content could not finish preparing. Your original PDF, cached pages and study records are preserved. Tap Retry preparation.") }
        }
    }
    fun cancel(pdfId: String) { tasks.filterKeys { it.startsWith("$pdfId:") }.values.forEach { it.cancel() } }
    private fun fail(pdfId: String, version: Long, message: String?) = update(pdfId,version,DocumentStudyWork(error=message))
    private fun update(pdfId: String, version: Long, work: DocumentStudyWork) {
        if (workspace.value.studyPlans[pdfId]?.preparationVersion == version) mutable.update { it + (pdfId to work) }
    }
}
