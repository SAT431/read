package dev.folio.runtime.assistant

import dev.folio.model.command.*

/** Only navigation/read requests may be retried automatically after partial progress. */
internal object AssistantContinuationPolicy {
    fun retrySafe(command: AppCommand): Boolean = when (command) {
        is AppCommand.Navigate, is AppCommand.OpenSubject, is AppCommand.OpenChapterLibrary,
        is AppCommand.SearchChapters, is AppCommand.OpenPdf, is AppCommand.PdfPage, is AppCommand.PdfZoom, is AppCommand.PdfTransform,
        is AppCommand.SetNotesFilter, is AppCommand.SearchNotes, is AppCommand.OpenSavedAnnotation,
        is AppCommand.SetLearningFilter, is AppCommand.OpenLearningObservation, is AppCommand.OpenRevision,
        is AppCommand.SearchStudy, is AppCommand.SearchPage, is AppCommand.OpenSearchResult,
        is AppCommand.ReviewQuizAttempt, is AppCommand.QuizQuestion -> true
        else -> false
    }
}
