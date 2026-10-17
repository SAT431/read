package dev.folio.runtime.state

import kotlinx.coroutines.CancellationException

/** Source-store failures must not prevent independent learning-state recovery. */
class WorkspaceStartupRecovery {
    companion object { const val PAGE_FAILURE = "Some readable page data could not be loaded. Its stored data is preserved." }
    suspend fun recover(loadLibrary: suspend () -> Unit, loadPages: suspend () -> Unit,
        restoreWorkspace: suspend () -> Unit): List<String> {
        val warnings = mutableListOf<String>()
        suspend fun attempt(message: String, block: suspend () -> Unit) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warnings.add(message) }
        }
        attempt("The PDF library could not be loaded. Its stored data is preserved.", loadLibrary)
        attempt(PAGE_FAILURE, loadPages)
        restoreWorkspace()
        return warnings
    }
}
