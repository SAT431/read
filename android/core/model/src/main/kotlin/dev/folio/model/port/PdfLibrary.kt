package dev.folio.model.port

import dev.folio.model.library.LibraryPdf
import kotlinx.coroutines.flow.StateFlow

interface PdfLibrary {
    val entries: StateFlow<List<LibraryPdf>>
    suspend fun load()
}
