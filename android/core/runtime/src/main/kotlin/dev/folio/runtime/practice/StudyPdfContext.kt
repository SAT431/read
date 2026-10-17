package dev.folio.runtime.practice

import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.CurriculumCatalog
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace

object StudyPdfContext {
    fun sync(state: Workspace, source: SourceAnchor, pdfs: List<LibraryPdf>, curriculum: CurriculumCatalog): Workspace {
        val pdf = pdfs.firstOrNull { it.document.id == source.documentId }
        val chapter = curriculum.chapters().firstOrNull { it.id == pdf?.chapterId }
        return state.copy(navigation = state.navigation.copy(pdfId = pdf?.id, pdfPageIndex = source.pageIndex,
            chapterId = chapter?.id, subject = chapter?.subject))
    }
}
