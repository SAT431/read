package dev.folio.model.command

import dev.folio.model.library.SubjectId
import dev.folio.model.workspace.AppDestination
import dev.folio.model.workspace.UserPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One semantic command surface for home/library controls and existing workspace capabilities. */
@Serializable
sealed interface AppCommand {
    @Serializable @SerialName("harness.analyze_pdf") data class HarnessAnalyzePdf(val pdfId: String) : AppCommand
    @Serializable @SerialName("harness.browse_topics") data class HarnessBrowseTopics(val pdfId: String, val query: String = "", val page: Int = 0) : AppCommand
    @Serializable @SerialName("harness.open_topic") data class HarnessOpenTopic(val pdfId: String, val expectedOutline: String, val topicId: String) : AppCommand
    @Serializable @SerialName("harness.inspect_topic") data class HarnessInspectTopic(val pdfId: String, val expectedOutline: String, val topicId: String) : AppCommand
    @Serializable @SerialName("harness.inspect_page") data class HarnessInspectPage(val pdfId: String, val pageIndex: Int) : AppCommand
    @Serializable @SerialName("harness.include_topics") data class HarnessIncludeTopics(val targetPdfId: String, val sourcePdfId: String,
        val expectedVersion: Long, val expectedOutline: String, val topicIds: List<String>) : AppCommand
    @Serializable @SerialName("harness.remove_source") data class HarnessRemoveSource(val targetPdfId: String, val sourcePdfId: String, val expectedVersion: Long) : AppCommand
    @Serializable @SerialName("study.analyze") data class AnalyzeStudy(val pdfId: String) : AppCommand
    @Serializable @SerialName("study.cancel") data class CancelStudyWork(val pdfId: String) : AppCommand
    @Serializable @SerialName("study.select_topics") data class SelectStudyTopics(val pdfId: String, val expectedOutline: String, val topicIds: List<String>) : AppCommand
    @Serializable @SerialName("study.begin") data class BeginStudy(val pdfId: String, val expectedVersion: Long) : AppCommand
    @Serializable @SerialName("study.topics") data class ShowStudyTopics(val pdfId: String) : AppCommand
    @Serializable @SerialName("study.guide") data class GuideStudy(val pdfId: String, val expectedVersion: Long, val enabled: Boolean) : AppCommand
    @Serializable @SerialName("study.finished_line") data class FinishStudyLine(val pdfId: String, val expectedVersion: Long, val sentenceId: String) : AppCommand
    @Serializable @SerialName("study.page") data class StudyPage(val pdfId: String, val expectedVersion: Long, val pageIndex: Int, val sourcePdfId: String? = null) : AppCommand
    @Serializable @SerialName("study.sentence") data class StudySentence(val pdfId: String, val expectedVersion: Long, val sentenceId: String) : AppCommand
    @Serializable @SerialName("search.query") data class SearchStudy(val text: String, val kind: dev.folio.model.search.SearchKind? = null) : AppCommand
    @Serializable @SerialName("search.page") data class SearchPage(val page: Int) : AppCommand
    @Serializable @SerialName("search.open") data class OpenSearchResult(val target: dev.folio.model.search.SearchTarget, val expectedFingerprint: String) : AppCommand
    @Serializable @SerialName("notes.filter") data class SetNotesFilter(val filter: dev.folio.model.source.NotesFilter) : AppCommand
    @Serializable @SerialName("notes.search") data class SearchNotes(val text: String) : AppCommand
    @Serializable @SerialName("notes.open") data class OpenSavedAnnotation(val activityId: String, val id: String, val expectedFingerprint: String) : AppCommand
    @Serializable @SerialName("notes.edit_note") data class BeginSavedNoteEdit(val activityId: String, val id: String, val expectedFingerprint: String) : AppCommand
    @Serializable @SerialName("notes.edit_draft") data class EditSavedDraft(val activityId: String, val id: String, val expectedFingerprint: String, val text: String) : AppCommand
    @Serializable @SerialName("notes.commit_draft") data class CommitSavedDraft(val activityId: String, val id: String, val expectedFingerprint: String, val newAnnotationId: String? = null, val asSeparateNote: Boolean = false) : AppCommand
    @Serializable @SerialName("notes.discard_draft") data class DiscardSavedDraft(val activityId: String, val id: String, val expectedFingerprint: String) : AppCommand
    @Serializable @SerialName("source.cache.check") data object CheckSourceCache : AppCommand
    @Serializable @SerialName("source.cache.confirm_repair") data class ConfirmSourceCacheRepair(val token: String) : AppCommand
    @Serializable @SerialName("source.cache.dismiss") data object DismissSourceCacheRepair : AppCommand
    @Serializable @SerialName("reader.checkpoints.show") data class ShowReadingCheckpoints(val activityId: String, val visible: Boolean) : AppCommand
    @Serializable @SerialName("reader.checkpoints.open") data class OpenReadingConfirmation(val activityId: String, val id: String, val expectedVersion: Long) : AppCommand
    @Serializable @SerialName("reader.confirm_read") data class ConfirmReading(val activityId: String, val source: dev.folio.model.source.SourceAnchor, val sentenceId: String? = null, val expectedReadingRevision: Long) : AppCommand
    @Serializable @SerialName("reader.clear_confirmation") data class ClearReadingConfirmation(val activityId: String, val id: String, val expectedVersion: Long) : AppCommand
    @Serializable @SerialName("reader.bookmark.add") data class AddReadingBookmark(val activityId: String, val id: String, val source: dev.folio.model.source.SourceAnchor, val sentenceId: String? = null) : AppCommand
    @Serializable @SerialName("reader.bookmark.remove") data class RemoveReadingBookmark(val activityId: String, val id: String, val expectedCreatedRevision: Long) : AppCommand
    @Serializable @SerialName("reader.bookmark.open") data class OpenReadingBookmark(val activityId: String, val id: String, val expectedCreatedRevision: Long) : AppCommand
    @Serializable @SerialName("reader.bookmarks.show") data class ShowReadingBookmarks(val activityId: String, val visible: Boolean) : AppCommand
    @Serializable @SerialName("quiz.timer.expand") data class SetPracticeTimerExpanded(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int, val expanded: Boolean) : AppCommand
    @Serializable @SerialName("quiz.timer.start") data class StartPracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : AppCommand
    @Serializable @SerialName("quiz.timer.pause") data class PausePracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : AppCommand
    @Serializable @SerialName("quiz.timer.reset") data class ResetPracticeTimer(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int) : AppCommand
    @Serializable @SerialName("quiz.timer.limit") data class SetPracticeTimerLimit(val activityId: String, val expectedAttempt: Int, val expectedVersion: Int, val seconds: Int?) : AppCommand
    @Serializable @SerialName("quiz.restart") data class RestartQuiz(val activityId: String, val expectedAttempt: Int) : AppCommand
    @Serializable @SerialName("quiz.review_attempt") data class ReviewQuizAttempt(val activityId: String,
        val attemptNumber: Int?, val questionId: String? = null) : AppCommand
    @Serializable @SerialName("quiz.confidence") data class SetAnswerConfidence(val activityId: String, val questionId: String, val expectedOption: Int,
        val expectedConfidence: dev.folio.model.activity.ConfidenceLevel?, val confidence: dev.folio.model.activity.ConfidenceLevel?) : AppCommand
    @Serializable @SerialName("learning.filter") data class SetLearningFilter(val filter: dev.folio.model.workspace.LearningFilter) : AppCommand
    @Serializable @SerialName("learning.open_observation") data class OpenLearningObservation(val id: String) : AppCommand
    @Serializable @SerialName("revision.accept_suggestion") data class AcceptRevisionSuggestion(val activityId: String,
        val questionId: String? = null, val expectedEvidence: String) : AppCommand
    @Serializable @SerialName("revision.schedule") data class ScheduleRevision(val activityId: String, val questionId: String? = null, val delayDays: Int) : AppCommand
    @Serializable @SerialName("revision.open") data class OpenRevision(val id: String) : AppCommand
    @Serializable @SerialName("revision.complete") data class CompleteRevision(val id: String, val expectedVersion: Int) : AppCommand
    @Serializable @SerialName("revision.remove") data class RemoveRevision(val id: String, val expectedVersion: Int) : AppCommand
    @Serializable @SerialName("app.navigate") data class Navigate(val destination: AppDestination) : AppCommand
    @Serializable @SerialName("app.back") data object Back : AppCommand
    @Serializable @SerialName("subject.open") data class OpenSubject(val subject: SubjectId) : AppCommand
    @Serializable @SerialName("chapter.open") data class OpenChapterLibrary(val chapterId: String) : AppCommand
    @Serializable @SerialName("library.search") data class SearchChapters(val text: String) : AppCommand
    @Serializable @SerialName("library.request_import") data class RequestPdfImport(val chapterId: String) : AppCommand
    @Serializable @SerialName("library.open_pdf") data class OpenPdf(val pdfId: String) : AppCommand
    @Serializable @SerialName("library.study_pdf") data class StudyPdf(val pdfId: String, val resume: Boolean = false) : AppCommand
    @Serializable @SerialName("source.engineer_page") data class EngineerPage(val documentId: String, val pageIndex: Int) : AppCommand
    @Serializable @SerialName("source.review") data class ReviewPage(val documentId: String, val pageIndex: Int) : AppCommand
    @Serializable @SerialName("source.review_region") data class ReviewRegion(val source: dev.folio.model.source.SourceAnchor?) : AppCommand
    @Serializable @SerialName("source.correction.propose") data class ProposeCorrection(val id: String,
        val source: dev.folio.model.source.SourceAnchor, val expectedOriginal: String, val replacement: String,
        val expectedApprovedId: String? = null) : AppCommand
    @Serializable @SerialName("source.correction.approve") data class ApproveCorrection(val id: String) : AppCommand
    @Serializable @SerialName("source.correction.remove") data class RemoveCorrection(val id: String) : AppCommand
    @Serializable @SerialName("source.correction.draft") data class SaveCorrectionDraft(val id: String,
        val draft: dev.folio.model.source.SourceCorrectionDraft?) : AppCommand
    @Serializable @SerialName("voice.start") data object StartVoice : AppCommand
    @Serializable @SerialName("voice.finish") data object FinishVoice : AppCommand
    @Serializable @SerialName("voice.cancel") data object CancelVoice : AppCommand
    @Serializable @SerialName("voice.review") data object ReviewVoice : AppCommand
    @Serializable @SerialName("voice.auto_send") data class VoiceAutoSend(val enabled: Boolean) : AppCommand
    @Serializable @SerialName("pdf.page") data class PdfPage(val pageIndex: Int) : AppCommand
    @Serializable @SerialName("pdf.next") data object NextPdfPage : AppCommand
    @Serializable @SerialName("pdf.previous") data object PreviousPdfPage : AppCommand
    @Serializable @SerialName("pdf.zoom") data class PdfZoom(val zoom: Float) : AppCommand
    @Serializable @SerialName("pdf.transform") data class PdfTransform(val zoom: Float, val panX: Float, val panY: Float) : AppCommand
    @Serializable @SerialName("preferences.set") data class SetPreferences(val preferences: UserPreferences) : AppCommand
    @Serializable @SerialName("workspace.command") data class WorkspaceAction(val command: WorkspaceCommand) : AppCommand
    @Serializable @SerialName("workspace.undo_layout") data object UndoLayout : AppCommand
    @Serializable @SerialName("quiz.select") data class SelectAnswer(val activityId: String, val questionId: String, val option: Int) : AppCommand
    @Serializable @SerialName("quiz.question") data class QuizQuestion(val activityId: String, val questionId: String) : AppCommand
    @Serializable @SerialName("quiz.submit") data class SubmitAnswer(val activityId: String, val questionId: String) : AppCommand
    @Serializable @SerialName("recall.response") data class RecallResponse(val activityId: String, val expectedText: String, val text: String) : AppCommand
    @Serializable @SerialName("recall.reveal") data class RevealRecall(val activityId: String) : AppCommand
    @Serializable @SerialName("recall.rate") data class RateRecall(val activityId: String, val rating: dev.folio.model.activity.RecallRating) : AppCommand
    @Serializable @SerialName("recall.restart") data class RestartRecall(val activityId: String) : AppCommand
    @Serializable @SerialName("reader.position") data class ReadingPosition(val activityId: String, val index: Int, val offset: Int) : AppCommand
    @Serializable @SerialName("reader.focus_sentence") data class FocusSentence(val activityId: String, val sentenceId: String) : AppCommand
    @Serializable @SerialName("annotation.create") data class CreateAnnotation(val activityId: String, val id: String,
        val source: dev.folio.model.source.SourceAnchor, val sentenceId: String? = null,
        val kind: dev.folio.model.source.AnnotationKind, val text: String = "") : AppCommand
    @Serializable @SerialName("annotation.draft") data class SaveAnnotationDraft(val activityId: String, val draftId: String,
        val draft: dev.folio.model.source.AnnotationDraft?) : AppCommand
    @Serializable @SerialName("annotation.update") data class UpdateAnnotation(val activityId: String, val id: String,
        val expectedText: String, val text: String) : AppCommand
    @Serializable @SerialName("annotation.remove") data class RemoveAnnotation(val activityId: String, val id: String,
        val expectedText: String) : AppCommand
    @Serializable @SerialName("component.pin") data class Pin(val componentId: String, val pinned: Boolean) : AppCommand
    @Serializable @SerialName("diagram.transform") data class Transform(val activityId: String, val zoom: Float, val panX: Float, val panY: Float) : AppCommand
}

@Serializable
data class AppCommandBatch(val schemaVersion: Int = 1, val requestId: String,
    val expectedRevision: Long? = null, val commands: List<AppCommand>)

sealed interface AppEffect {
    data class AnalyzeStudy(val pdfId: String, val requestId: String = "") : AppEffect
    data class CancelStudy(val pdfId: String) : AppEffect
    data class PrepareStudy(val pdfId: String, val version: Long) : AppEffect
    data object CheckSourceCache : AppEffect
    data class ConfirmSourceCacheRepair(val token: String) : AppEffect
    data object DismissSourceCacheRepair : AppEffect
    data class RequestPdfImport(val chapterId: String) : AppEffect
    data class EngineerPage(val request: dev.folio.model.port.EngineeringRequest) : AppEffect
    data object StartVoice : AppEffect
    data object FinishVoice : AppEffect
    data object CancelVoice : AppEffect
    data object ReviewVoice : AppEffect
}
