package dev.folio.model.library

import kotlinx.serialization.Serializable

@Serializable
enum class SubjectId { PHYSICS, CHEMISTRY, BOTANY, ZOOLOGY }
data class Subject(val id: SubjectId, val title: String, val description: String)

data class StudyChapter(val id: String, val subject: SubjectId, val title: String,
    val syllabusUnit: Int, val order: Int, val sharedBiology: Boolean = false)
