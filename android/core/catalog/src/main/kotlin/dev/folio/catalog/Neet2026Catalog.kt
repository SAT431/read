package dev.folio.catalog

import dev.folio.model.library.*
import dev.folio.model.port.CurriculumCatalog

class Neet2026Catalog : CurriculumCatalog {
    override val baseline = "NEET UG 2026"
    override val sourceUrl = "https://neet.nta.nic.in/document/syllabus-for-neet-ug-2026-examination/"
    override fun subjects() = listOf(
        Subject(SubjectId.PHYSICS, "Physics", "Motion, energy and the laws of nature"),
        Subject(SubjectId.CHEMISTRY, "Chemistry", "Matter, reactions and molecular connections"),
        Subject(SubjectId.BOTANY, "Botany", "Plants, cells, inheritance and ecosystems"),
        Subject(SubjectId.ZOOLOGY, "Zoology", "Animals, human systems and evolution"),
    )
    override fun chapters() = PhysicsChapters.all + ChemistryChapters.all + BotanyChapters.all + ZoologyChapters.all
}
