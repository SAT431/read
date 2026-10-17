package dev.folio.runtime.assistant

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.AppEffect
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select

internal class PreparationContextChanged : Exception()
/** Viewport-only updates can follow freshly loaded text; learner inputs/target changes cannot. */
internal object AssistantPreparationWait {
    fun sameStudy(expected: Workspace, current: Workspace) = normalize(expected) == normalize(current)
    private fun normalize(state: Workspace) = state.copy(revision = 0, preferences = UserPreferences(),
        navigation = state.navigation.copy(pdfZoom = 1f, pdfPanX = 0f, pdfPanY = 0f),
        activities = state.activities.mapValues { (_, activity) -> when (val data = activity.data) {
            is ActivityData.Reader -> activity.copy(data = data.copy(scrollIndex = 0, scrollOffset = 0, pagePositions = emptyMap()))
            is ActivityData.Quiz -> activity.copy(data = data.copy(timer = data.timer.copy(elapsedMillis = 0)))
            else -> activity
        } })
    suspend fun await(awaiter: AssistantEffectAwaiter, effects: List<AppEffect>, expected: Workspace,
        workspace: StateFlow<Workspace>, library: PdfLibrary, pdfs: List<LibraryPdf>): List<PreparedSourceResult> = coroutineScope {
        fun current() = sameStudy(expected, workspace.value) && library.entries.value == pdfs
        if (!current()) throw PreparationContextChanged()
        val changed = async(start = CoroutineStart.UNDISPATCHED) {
            combine(workspace, library.entries) { saved, entries -> sameStudy(expected, saved) && entries == pdfs }.first { !it }
        }
        val ready = async { awaiter.await(effects) }
        try {
            val result = select<List<PreparedSourceResult>> {
                changed.onAwait { throw PreparationContextChanged() }
                ready.onAwait { it }
            }
            if (!current()) throw PreparationContextChanged()
            result
        } finally { changed.cancel(); ready.cancel() }
    }
}
