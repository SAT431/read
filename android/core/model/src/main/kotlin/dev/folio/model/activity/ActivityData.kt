package dev.folio.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Learning state survives removal of every visual component that represents it. */
@Serializable
sealed interface ActivityData {
    @Serializable @SerialName("reader")
    data class Reader(val selectedSentence: String? = null, val scrollIndex: Int = 0,
        val scrollOffset: Int = 0, val notes: Map<String, String> = emptyMap(),
        val pagePositions: Map<String, ReadingPosition> = emptyMap(),
        val annotations: Map<String, dev.folio.model.source.SourceAnnotation> = emptyMap(),
        val annotationDrafts: Map<String, dev.folio.model.source.AnnotationDraft> = emptyMap(),
        val bookmarks: Map<String, dev.folio.model.source.ReadingBookmark> = emptyMap(),
        val bookmarksVisible: Boolean = false, val bookmarkJump: Int = 0, val bookmarkRevision: Long = 0,
        val readingConfirmations: Map<String, dev.folio.model.source.ReadingConfirmation> = emptyMap(),
        val readingRevision: Long = 0, val readingCheckpointsVisible: Boolean = false) : ActivityData
    @Serializable @SerialName("quiz")
    data class Quiz(val questions: List<Question>, val index: Int = 0,
        val answers: Map<String, Int> = emptyMap(), val submitted: Set<String> = emptySet(),
        val confidence: Map<String, AnswerConfidence> = emptyMap(), val attemptNumber: Int = 1,
        val checks: Map<String, QuizCheck> = emptyMap(),
        val selectionActors: Map<String, dev.folio.model.command.CommandActor> = emptyMap(),
        val pastAttempts: List<QuizAttempt> = emptyList(), val review: QuizHistorySelection? = null,
        val timer: PracticeTimer = PracticeTimer()) : ActivityData
    @Serializable @SerialName("recall")
    data class Recall(val prompt: String, val referenceAnswer: String, val sources: List<dev.folio.model.source.SourceAnchor>,
        val response: String = "", val revealed: Boolean = false, val rating: RecallRating? = null,
        val ratedAtEpochMillis: Long? = null, val responseByAssistant: Boolean = false, val ratingByAssistant: Boolean = false,
        val pastAttempts: List<RecallAttempt> = emptyList()) : ActivityData
    @Serializable @SerialName("explanation")
    data class Explanation(val text: String) : ActivityData
    @Serializable @SerialName("comparison")
    data class Comparison(val headers: List<String>, val rows: List<List<String>>) : ActivityData
    @Serializable @SerialName("diagram")
    data class Diagram(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f,
        val hiddenLabels: Set<String> = emptySet()) : ActivityData
}
