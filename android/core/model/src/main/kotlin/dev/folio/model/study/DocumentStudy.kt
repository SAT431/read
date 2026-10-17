package dev.folio.model.study

import kotlinx.serialization.Serializable

@Serializable data class DocumentTopic(val id: String, val title: String, val summary: String,
    val firstPage: Int, val lastPage: Int, val top: Float = 0f, val bottom: Float = 1000f,
    val hasFigures: Boolean = false, val subtopics: List<String> = emptyList())
@Serializable data class DocumentOutline(val documentId: String, val checksum: String, val pageCount: Int,
    val indexedPages: List<Int> = emptyList(), val topics: List<DocumentTopic> = emptyList(), val schemaVersion: Int = 1)
@Serializable enum class StudyMode { TOPICS, READING }
@Serializable data class StudyPlacement(val afterPdfId: String, val afterPageIndex: Int)
@Serializable data class StudySourceSelection(val pdfId: String, val checksum: String, val outlineFingerprint: String,
    val topicIds: List<String>, val pages: List<Int>, val placement: StudyPlacement? = null)
@Serializable data class DocumentStudyPlan(val pdfId: String, val checksum: String, val outlineFingerprint: String,
    val topicIds: List<String>, val pages: List<Int>, val version: Long = 1, val mode: StudyMode = StudyMode.TOPICS,
    val guided: Boolean = false, val sentenceId: String? = null, val pageIndex: Int = 0, val preparationVersion: Long = 0,
    val attachedSources: List<StudySourceSelection> = emptyList(), val activeSourcePdfId: String? = null)
data class DocumentStudyWork(val busy: Boolean = false, val completed: Int = 0, val total: Int = 0,
    val message: String = "", val error: String? = null)
