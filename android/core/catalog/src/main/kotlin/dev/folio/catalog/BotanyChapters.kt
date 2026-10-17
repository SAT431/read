package dev.folio.catalog

import dev.folio.model.library.*

internal object BotanyChapters {
    val all = listOf(
        StudyChapter("botany-01", SubjectId.BOTANY, "The Living World", 1, 1, true),
        StudyChapter("botany-02", SubjectId.BOTANY, "Biological Classification", 1, 2, true),
        StudyChapter("botany-03", SubjectId.BOTANY, "Plant Kingdom", 1, 3, false),
        StudyChapter("botany-04", SubjectId.BOTANY, "Morphology of Flowering Plants", 2, 4, false),
        StudyChapter("botany-05", SubjectId.BOTANY, "Anatomy of Flowering Plants", 2, 5, false),
        StudyChapter("botany-06", SubjectId.BOTANY, "Cell: The Unit of Life", 3, 6, true),
        StudyChapter("botany-07", SubjectId.BOTANY, "Biomolecules", 3, 7, true),
        StudyChapter("botany-08", SubjectId.BOTANY, "Cell Cycle and Cell Division", 3, 8, true),
        StudyChapter("botany-09", SubjectId.BOTANY, "Photosynthesis in Higher Plants", 4, 9, false),
        StudyChapter("botany-10", SubjectId.BOTANY, "Respiration in Plants", 4, 10, false),
        StudyChapter("botany-11", SubjectId.BOTANY, "Plant Growth and Development", 4, 11, false),
        StudyChapter("botany-12", SubjectId.BOTANY, "Sexual Reproduction in Flowering Plants", 6, 12, false),
        StudyChapter("botany-13", SubjectId.BOTANY, "Principles of Inheritance and Variation", 7, 13, true),
        StudyChapter("botany-14", SubjectId.BOTANY, "Molecular Basis of Inheritance", 7, 14, true),
        StudyChapter("botany-15", SubjectId.BOTANY, "Biotechnology: Principles and Processes", 9, 15, true),
        StudyChapter("botany-16", SubjectId.BOTANY, "Biotechnology and its Applications", 9, 16, true),
        StudyChapter("botany-17", SubjectId.BOTANY, "Organisms and Populations", 10, 17, true),
        StudyChapter("botany-18", SubjectId.BOTANY, "Ecosystem", 10, 18, true),
        StudyChapter("botany-19", SubjectId.BOTANY, "Biodiversity and Conservation", 10, 19, true),
    )
}
