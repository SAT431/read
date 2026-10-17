package dev.folio.catalog

import dev.folio.model.library.*

internal object ZoologyChapters {
    val all = listOf(
        StudyChapter("zoology-01", SubjectId.ZOOLOGY, "Animal Kingdom", 1, 1, false),
        StudyChapter("zoology-02", SubjectId.ZOOLOGY, "Structural Organisation in Animals", 2, 2, false),
        StudyChapter("zoology-03", SubjectId.ZOOLOGY, "Breathing and Exchange of Gases", 5, 3, false),
        StudyChapter("zoology-04", SubjectId.ZOOLOGY, "Body Fluids and Circulation", 5, 4, false),
        StudyChapter("zoology-05", SubjectId.ZOOLOGY, "Excretory Products and their Elimination", 5, 5, false),
        StudyChapter("zoology-06", SubjectId.ZOOLOGY, "Locomotion and Movement", 5, 6, false),
        StudyChapter("zoology-07", SubjectId.ZOOLOGY, "Neural Control and Coordination", 5, 7, false),
        StudyChapter("zoology-08", SubjectId.ZOOLOGY, "Chemical Coordination and Integration", 5, 8, false),
        StudyChapter("zoology-09", SubjectId.ZOOLOGY, "Human Reproduction", 6, 9, false),
        StudyChapter("zoology-10", SubjectId.ZOOLOGY, "Reproductive Health", 6, 10, false),
        StudyChapter("zoology-11", SubjectId.ZOOLOGY, "Evolution", 7, 11, true),
        StudyChapter("zoology-12", SubjectId.ZOOLOGY, "Human Health and Disease", 8, 12, false),
        StudyChapter("zoology-13", SubjectId.ZOOLOGY, "Microbes in Human Welfare", 8, 13, true),
    )
}
