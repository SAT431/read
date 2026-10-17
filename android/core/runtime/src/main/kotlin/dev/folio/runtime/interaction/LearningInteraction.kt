package dev.folio.runtime.interaction

sealed interface LearningInteraction {
    data class ShowReadingCheckpoints(val activityId: String, val visible: Boolean) : LearningInteraction
    data class OpenReadingConfirmation(val activityId: String, val id: String, val expectedVersion: Long) : LearningInteraction
    data class ConfirmReading(val activityId: String, val source: dev.folio.model.source.SourceAnchor, val sentenceId: String? = null, val expectedReadingRevision: Long) : LearningInteraction
    data class ClearReadingConfirmation(val activityId: String, val id: String, val expectedVersion: Long) : LearningInteraction
    data class AddReadingBookmark(val activityId: String, val id: String, val source: dev.folio.model.source.SourceAnchor, val sentenceId: String? = null) : LearningInteraction
    data class RemoveReadingBookmark(val activityId: String, val id: String, val expectedCreatedRevision: Long) : LearningInteraction
    data class OpenReadingBookmark(val activityId: String, val id: String, val expectedCreatedRevision: Long) : LearningInteraction
    data class ShowReadingBookmarks(val activityId: String, val visible: Boolean) : LearningInteraction
    data class SetPracticeTimerExpanded(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int, val expanded: Boolean) : LearningInteraction
    data class StartPracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : LearningInteraction
    data class PausePracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : LearningInteraction
    data class ResetPracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : LearningInteraction
    data class SetPracticeTimerLimit(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int, val seconds: Int?) : LearningInteraction
    data class RestartQuiz(val activityId: String, val expectedAttempt: Int) : LearningInteraction
    data class ReviewQuizAttempt(val activityId: String, val attemptNumber: Int?, val questionId: String?) : LearningInteraction
    data class SetAnswerConfidence(val activityId: String, val questionId: String, val expectedOption: Int,
        val expectedConfidence: dev.folio.model.activity.ConfidenceLevel?, val confidence: dev.folio.model.activity.ConfidenceLevel?) : LearningInteraction
    data class AcceptRevisionSuggestion(val activityId: String, val questionId: String?, val expectedEvidence: String) : LearningInteraction
    data class ScheduleRevision(val activityId: String, val questionId: String?, val delayDays: Int) : LearningInteraction
    data class RecallResponse(val activityId: String, val expectedText: String, val text: String) : LearningInteraction
    data class RevealRecall(val activityId: String) : LearningInteraction
    data class RateRecall(val activityId: String, val rating: dev.folio.model.activity.RecallRating) : LearningInteraction
    data class RestartRecall(val activityId: String) : LearningInteraction
    data class SelectAnswer(val activityId: String, val questionId: String, val option: Int) : LearningInteraction
    data class QuizQuestion(val activityId: String, val questionId: String) : LearningInteraction
    data class SubmitAnswer(val activityId: String, val questionId: String) : LearningInteraction
    data class FocusSentence(val activityId: String, val sentenceId: String) : LearningInteraction
    data class SetReadingPosition(val activityId: String, val index: Int, val offset: Int) : LearningInteraction
    data class TransformDiagram(val activityId: String, val zoom: Float, val panX: Float, val panY: Float) : LearningInteraction
    data class PinComponent(val componentId: String, val pinned: Boolean) : LearningInteraction
    data class CreateAnnotation(val activityId: String, val id: String, val source: dev.folio.model.source.SourceAnchor,
        val sentenceId: String?, val kind: dev.folio.model.source.AnnotationKind, val text: String) : LearningInteraction
    data class UpdateAnnotation(val activityId: String, val id: String, val expectedText: String, val text: String) : LearningInteraction
    data class SaveAnnotationDraft(val activityId: String, val draftId: String, val draft: dev.folio.model.source.AnnotationDraft?) : LearningInteraction
    data class RemoveAnnotation(val activityId: String, val id: String, val expectedText: String) : LearningInteraction
}
