package dev.folio.runtime.action

import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.CurriculumCatalog
import dev.folio.model.workspace.*

class LibraryNavigationReducer(private val curriculum: CurriculumCatalog) {
    fun reduce(state: Workspace, command: AppCommand, pdfs: List<LibraryPdf>): Workspace? {
        val nav = state.navigation
        val next = when (command) {
            is AppCommand.Navigate -> {
                require(command.destination in setOf(AppDestination.HOME, AppDestination.WORKSPACE, AppDestination.EXPLORER, AppDestination.SETTINGS, AppDestination.ASSISTANT, AppDestination.REVISION, AppDestination.LEARNING, AppDestination.NOTES, AppDestination.SEARCH))
                nav.copy(destination = command.destination, assistantOrigin = if (command.destination == AppDestination.ASSISTANT)
                    nav.assistantOrigin.takeIf { nav.destination == AppDestination.ASSISTANT } ?: nav.destination else nav.assistantOrigin)
            }
            AppCommand.Back -> if (nav.destination == AppDestination.PDF && nav.studyOriginalReturn)
                nav.copy(destination = AppDestination.STUDY, pdfId = nav.studyReturnPdfId ?: nav.pdfId,
                    chapterId = pdfs.firstOrNull { it.id == (nav.studyReturnPdfId ?: nav.pdfId) }?.chapterId ?: nav.chapterId,
                    subject = curriculum.chapters().firstOrNull { chapter -> chapter.id == pdfs.firstOrNull { it.id == (nav.studyReturnPdfId ?: nav.pdfId) }?.chapterId }?.subject ?: nav.subject,
                    studyOriginalReturn = false, studyReturnPdfId = null)
            else if (nav.destination == AppDestination.PDF && nav.sourceReviewVisible)
                nav.copy(sourceReviewVisible = false) else nav.copy(destination = when (nav.destination) {
                AppDestination.SUBJECT -> AppDestination.HOME
                AppDestination.CHAPTER -> AppDestination.SUBJECT
                AppDestination.PDF -> AppDestination.CHAPTER
                AppDestination.STUDY -> AppDestination.CHAPTER
                AppDestination.ASSISTANT -> nav.assistantOrigin ?: AppDestination.HOME
                else -> AppDestination.HOME
            })
            is AppCommand.OpenSubject -> {
                require(curriculum.subjects().any { it.id == command.subject })
                AppNavigation(AppDestination.SUBJECT, subject = command.subject)
            }
            is AppCommand.OpenChapterLibrary -> {
                val chapter = curriculum.chapters().firstOrNull { it.id == command.chapterId } ?: error("Unknown curriculum chapter")
                AppNavigation(AppDestination.CHAPTER, chapter.subject, chapter.id)
            }
            is AppCommand.SearchStudy -> { require(command.text.length <= 200); nav.copy(destination = AppDestination.SEARCH,
                globalQuery = command.text, searchKind = command.kind, searchPage = 0) }
            is AppCommand.SearchPage -> { require(command.page in 0..1_000_000); nav.copy(destination = AppDestination.SEARCH, searchPage = command.page) }
            is AppCommand.SetNotesFilter -> nav.copy(destination = AppDestination.NOTES, notesFilter = command.filter)
            is AppCommand.SearchNotes -> { require(command.text.length <= 200); nav.copy(destination = AppDestination.NOTES, notesQuery = command.text) }
            is AppCommand.SetLearningFilter -> nav.copy(destination = AppDestination.LEARNING, learningFilter = command.filter)
            is AppCommand.SearchChapters -> { require(command.text.length <= 200); nav.copy(search = command.text) }
            is AppCommand.ReviewPage -> {
                val pdf = pdfs.firstOrNull { it.document.id == command.documentId } ?: error("Unknown imported PDF")
                require(command.pageIndex in 0 until pdf.document.pageCount) { "PDF page boundary reached" }
                val chapter = curriculum.chapters().first { it.id == pdf.chapterId }
                AppNavigation(AppDestination.PDF, chapter.subject, chapter.id, pdf.id, command.pageIndex, sourceReviewVisible = true)
            }
            is AppCommand.OpenPdf -> {
                val pdf = pdfs.firstOrNull { it.id == command.pdfId } ?: error("Unknown PDF")
                val chapter = curriculum.chapters().first { it.id == pdf.chapterId }
                val originalContext = nav.destination == AppDestination.PDF ||
                    (nav.destination == AppDestination.ASSISTANT && nav.assistantOrigin == AppDestination.PDF)
                val position = if (nav.pdfId == pdf.id && originalContext) nav.pdfPageIndex
                    else state.context.source?.takeIf { it.documentId == pdf.document.id }?.pageIndex ?: 0
                AppNavigation(AppDestination.PDF, chapter.subject, chapter.id, pdf.id, position.coerceIn(0, pdf.document.pageCount - 1),
                    studyOriginalReturn = nav.destination == AppDestination.STUDY,
                    studyReturnPdfId = nav.pdfId.takeIf { nav.destination == AppDestination.STUDY })
            }
            is AppCommand.PdfPage -> pdfPage(nav, pdfs, command.pageIndex)
            AppCommand.NextPdfPage -> pdfPage(nav, pdfs, nav.pdfPageIndex + 1)
            AppCommand.PreviousPdfPage -> pdfPage(nav, pdfs, nav.pdfPageIndex - 1)
            is AppCommand.PdfZoom -> {
                require(nav.pdfId != null && command.zoom.isFinite() && command.zoom in .5f..4f)
                nav.copy(destination = AppDestination.PDF, pdfZoom = command.zoom)
            }
            is AppCommand.PdfTransform -> {
                require(nav.pdfId != null && command.zoom.isFinite() && command.panX.isFinite() && command.panY.isFinite())
                require(command.zoom in .5f..4f && command.panX in -2000f..2000f && command.panY in -2000f..2000f)
                nav.copy(destination = AppDestination.PDF, pdfZoom = command.zoom, pdfPanX = command.panX, pdfPanY = command.panY)
            }
            is AppCommand.SetPreferences -> {
                require(command.preferences.readingSizeSp in 14..36)
                return state.copy(preferences = command.preferences)
            }
            else -> return null
        }
        return state.copy(navigation = next)
    }
    private fun pdfPage(nav: AppNavigation, pdfs: List<LibraryPdf>, index: Int): AppNavigation {
        val pdf = pdfs.firstOrNull { it.id == nav.pdfId } ?: error("No active PDF")
        require(index in 0 until pdf.document.pageCount) { "PDF page boundary reached" }
        return nav.copy(destination = AppDestination.PDF, pdfPageIndex = index, pdfZoom = 1f, pdfPanX = 0f, pdfPanY = 0f, reviewElementId = null)
    }
}
