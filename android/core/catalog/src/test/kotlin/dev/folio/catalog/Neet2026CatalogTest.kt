package dev.folio.catalog

import dev.folio.model.library.SubjectId
import org.junit.Assert.*
import org.junit.Test

class Neet2026CatalogTest {
    private val catalog = Neet2026Catalog()
    @Test fun fourSubjectsHaveStableUniqueChapterLibraries() {
        assertEquals(SubjectId.entries.toSet(), catalog.subjects().map { it.id }.toSet())
        val chapters = catalog.chapters()
        assertEquals(72, chapters.size)
        assertEquals(chapters.size, chapters.map { it.id }.distinct().size)
        assertEquals(19, chapters.count { it.subject == SubjectId.BOTANY })
        assertEquals(13, chapters.count { it.subject == SubjectId.ZOOLOGY })
    }
    @Test fun coverageIncludesAllOfficialSyllabusUnitNumbers() {
        for (subject in listOf(SubjectId.PHYSICS, SubjectId.CHEMISTRY)) {
            assertEquals((1..20).toSet(), catalog.chapters().filter { it.subject == subject }.map { it.syllabusUnit }.toSet())
        }
        assertEquals((1..10).toSet(), catalog.chapters().filter { it.subject in setOf(SubjectId.BOTANY, SubjectId.ZOOLOGY) }.map { it.syllabusUnit }.toSet())
        assertTrue(catalog.chapters().any { it.sharedBiology })
    }
}
