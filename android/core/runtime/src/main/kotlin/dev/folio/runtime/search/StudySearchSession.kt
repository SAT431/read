package dev.folio.runtime.search

import dev.folio.model.port.*
import dev.folio.model.search.StudySearchSnapshot
import dev.folio.model.workspace.Workspace
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Cancellable background projection. Stale results are never represented as current provider context. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudySearchSession(scope: CoroutineScope, workspace: StateFlow<Workspace>, private val library: PdfLibrary,
    private val catalog: SourceCatalog, sourceChanges: StateFlow<Long>, curriculum: CurriculumCatalog) {
    private val mutableState = MutableStateFlow(StudySearchSnapshot())
    private val engine = StudySearchEngine(curriculum, catalog)
    val state = mutableState.asStateFlow()
    companion object {
        fun active(state: Workspace) = state.navigation.destination == dev.folio.model.workspace.AppDestination.SEARCH ||
            (state.navigation.destination == dev.folio.model.workspace.AppDestination.ASSISTANT && state.navigation.assistantOrigin == dev.folio.model.workspace.AppDestination.SEARCH)
    }
    init { scope.launch {
        combine(workspace, library.entries, sourceChanges) { saved, pdfs, _ -> saved to pdfs }
            .collectLatest { (saved, pdfs) ->
                val result = try { withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    if (active(saved)) engine.search(saved, pdfs) { context.ensureActive() } else StudySearchSnapshot(saved.revision, catalog.revision,
                        saved.navigation.globalQuery, saved.navigation.searchKind, saved.navigation.searchPage)
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { StudySearchSnapshot(saved.revision, catalog.revision, saved.navigation.globalQuery,
                    saved.navigation.searchKind, saved.navigation.searchPage, error = "Search could not finish. Your saved records are unchanged.") }
                if (saved == workspace.value && pdfs == library.entries.value && result.sourceRevision == catalog.revision) mutableState.value = result.copy(libraryEntries = pdfs)
            }
    } }
    fun snapshotFor(saved: Workspace): StudySearchSnapshot? = state.value.takeIf {
        active(saved) && it.libraryEntries == library.entries.value &&
        it.workspaceRevision == saved.revision && it.sourceRevision == catalog.revision && it.query == saved.navigation.globalQuery &&
            it.kind == saved.navigation.searchKind && it.page == saved.navigation.searchPage
    }
    suspend fun awaitCurrent(saved: Workspace) {
        if (!active(saved) || saved.navigation.globalQuery.isBlank()) return
        withTimeoutOrNull(5000) { state.first { snapshotFor(saved) != null } }
    }
}
