package dev.folio.model.port

import dev.folio.model.library.*

interface CurriculumCatalog {
    val baseline: String
    val sourceUrl: String
    fun subjects(): List<Subject>
    fun chapters(): List<StudyChapter>
}
