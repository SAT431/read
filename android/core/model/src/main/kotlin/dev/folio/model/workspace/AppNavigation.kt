package dev.folio.model.workspace

import dev.folio.model.library.SubjectId
import kotlinx.serialization.Serializable

@Serializable enum class AppDestination { HOME, SUBJECT, CHAPTER, PDF, WORKSPACE, EXPLORER, SETTINGS, ASSISTANT, REVISION, LEARNING, NOTES, SEARCH, STUDY }
@Serializable
data class AppNavigation(val destination: AppDestination = AppDestination.HOME,
    val subject: SubjectId? = null, val chapterId: String? = null, val pdfId: String? = null,
    val pdfPageIndex: Int = 0, val pdfZoom: Float = 1f, val search: String = "",
    val pdfPanX: Float = 0f, val pdfPanY: Float = 0f, val assistantOrigin: AppDestination? = null,
    val sourceReviewVisible: Boolean = false, val reviewElementId: String? = null,
    val learningFilter: LearningFilter = LearningFilter.ALL,
    val notesFilter: dev.folio.model.source.NotesFilter = dev.folio.model.source.NotesFilter.ALL, val notesQuery: String = "", val globalQuery: String = "",
    val searchKind: dev.folio.model.search.SearchKind? = null, val searchPage: Int = 0, val studyOriginalReturn: Boolean = false,
    val studyReturnPdfId: String? = null, val harnessPdfId: String? = null,
    val harnessTopicQuery: String = "", val harnessTopicPage: Int = 0)
