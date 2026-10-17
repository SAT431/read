package dev.folio.runtime.action

import dev.folio.model.command.AppCommand
import kotlinx.serialization.Serializable

@Serializable
data class AppActionContract(val id: String, val description: String, val arguments: Map<String, String>,
    val requiresUserPlatformInteraction: Boolean = false, val enabled: Boolean = true)

/** API prompts discover the same semantic actions that every native control dispatches. */
object AppActionRegistry {
    val all = listOf(
        action("harness.analyze_pdf", "Analyze or resume any imported PDF's topic map without leaving the current study", "pdfId" to "existing library PDF ID"),
        action("harness.browse_topics", "Discover exact source-linked topic IDs in any PDF, analyzing it first if needed; results use fresh appContext.documentStudy.browse", "pdfId" to "library PDF ID", "query" to "optional topic title/subtopic search up to 200 characters", "page" to "zero-based page of 40 topic results"),
        action("harness.open_topic", "Open one exact linked topic in its PDF study and prepare native text/original diagram crops", "pdfId" to "existing PDF ID", "expectedOutline" to "source map fingerprint", "topicId" to "native topic ID"),
        action("harness.inspect_topic", "Inspect a topic's exact original PDF page; Back returns to the current study collection", "pdfId" to "source PDF ID", "expectedOutline" to "source map fingerprint", "topicId" to "native topic ID"),
        action("harness.inspect_page", "Inspect an exact original page while retaining the current study return destination", "pdfId" to "existing library PDF ID", "pageIndex" to "zero-based original PDF page index"),
        action("harness.include_topics", "Insert linked topics from another PDF into the current reading page and prepare original text/images; native placement preserves source ownership and current position", "targetPdfId" to "current study PDF ID", "sourcePdfId" to "other library PDF ID", "expectedVersion" to "current destination plan.version", "expectedOutline" to "source map fingerprint", "topicIds" to "nonempty source topic IDs"),
        action("harness.remove_source", "Remove only an attached source reference from the study collection, retaining original PDFs/cache/learner records", "targetPdfId" to "current study PDF ID", "sourcePdfId" to "attached source PDF ID", "expectedVersion" to "current plan.version"),
        action("study.analyze", "Resume original PDF topic mapping; completed batches are cached", "pdfId" to "current study PDF ID"),
        action("study.cancel", "Pause document analysis or selected-page preparation; preserve completed work", "pdfId" to "current study PDF ID"),
        action("study.select_topics", "Select today's exact native topic IDs from the complete topic map", "pdfId" to "current study PDF ID", "expectedOutline" to "appContext.documentStudy.outlineFingerprint", "topicIds" to "nonempty list of current native topic IDs"),
        action("study.begin", "Prepare selected original pages and diagrams for full-screen reading", "pdfId" to "current study PDF ID", "expectedVersion" to "current plan.version"),
        action("study.topics", "Return to the topic map while retaining reading reports", "pdfId" to "current study PDF ID"),
        action("study.guide", "Start or pause sentence-by-sentence guided reading", "pdfId" to "current study PDF ID", "expectedVersion" to "current plan.version", "enabled" to "boolean"),
        action("study.finished_line", "Only on explicit learner confirmation: record current highlighted sentence read and advance", "pdfId" to "current study PDF ID", "expectedVersion" to "current plan.version", "sentenceId" to "exact current plan.sentenceId"),
        action("study.page", "Open a prepared linked page without marking it read", "pdfId" to "destination study PDF ID", "expectedVersion" to "current plan.version", "pageIndex" to "selected zero-based page index", "sourcePdfId" to "optional PDF ID in the collection; defaults to active source"),
        action("study.sentence", "Highlight a selected prepared sentence without marking it read", "pdfId" to "current study PDF ID", "expectedVersion" to "current plan.version", "sentenceId" to "native sentence ID in current selected content"),
        action("search.query", "Search native chapters/PDFs/cached passages/bookmarks/saved notes/quiz history/recall prompts; never private drafts", "text" to "max 200 characters", "kind" to "CHAPTER|PDF|PASSAGE|BOOKMARK|NOTE|QUIZ|RECALL or null for all"),
        action("search.page", "Show another page of native search results", "page" to "zero-based 0..1000000"),
        action("search.open", "Open exact native current search result with source/pin/stale guards", "target" to "search.results target", "expectedFingerprint" to "search.results fingerprint"),
        action("notes.filter", "Show saved annotations/private draft counts with a native filter", "filter" to "ALL|NOTES|MARKS|DRAFTS|UNAVAILABLE"),
        action("notes.search", "Search the native saved-notes view; unfinished draft matches stay local", "text" to "max 200 characters"),
        action("notes.open", "Resume a saved annotation's currently resolvable passage/sentence", "activityId" to "savedAnnotations activityId", "id" to "annotation id", "expectedFingerprint" to "native annotation fingerprint"),
        action("notes.edit_note", "Native learner-only begin private note edit", "activityId" to "reader ID", "id" to "note ID", "expectedFingerprint" to "native fingerprint"),
        action("notes.edit_draft", "Native learner-only update retained private draft", "activityId" to "reader ID", "id" to "draft ID", "expectedFingerprint" to "native fingerprint", "text" to "max 2000 characters"),
        action("notes.commit_draft", "Native learner-only save a draft retaining original source", "activityId" to "reader ID", "id" to "draft ID", "expectedFingerprint" to "native fingerprint", "newAnnotationId" to "new ID for new/separate note; null for existing edit", "asSeparateNote" to "native explicit copy of retained linked edit"),
        action("notes.discard_draft", "Native learner-only confirmed discard of one exact private draft", "activityId" to "reader ID", "id" to "draft ID", "expectedFingerprint" to "native fingerprint"),
        action("source.cache.check", "Check cached readable pages and open native repair preview; never approves repair"),
        AppActionContract("source.cache.confirm_repair", "Native learner-only confirmation; never available to AI", mapOf("token" to "native confirmation token"), true),
        action("source.cache.dismiss", "Dismiss cache check/repair preview without changing stored data"),
        action("reader.checkpoints.show", "Show/hide all saved reading reports for the reader, including outdated targets", "activityId" to "visible active reader", "visible" to "boolean"),
        action("reader.checkpoints.open", "Resume an unchanged saved reading target without creating another reading report", "activityId" to "owner reader", "id" to "saved reading confirmation ID", "expectedVersion" to "exact record.version"),
        action("reader.confirm_read", "Record only the learner's explicit report of having read a passage/sentence", "activityId" to "visible active reader", "source" to "current readable passage source", "sentenceId" to "reported native sentence ID or null for reported whole passage", "expectedReadingRevision" to "reader.readingRevision"),
        action("reader.clear_confirmation", "Clear only the explicitly requested reading report without changing source/notes", "activityId" to "visible active reader", "id" to "native reading confirmation ID", "expectedVersion" to "exact record.version"),
        action("reader.bookmark.add", "Save a native exact reading position only when requested", "activityId" to "visible active reader", "id" to "new unique identifier", "source" to "current page/element source; null element saves current scroll position", "sentenceId" to "current source sentence ID or null"),
        action("reader.bookmark.remove", "Remove only the explicitly requested saved bookmark", "activityId" to "visible active reader", "id" to "saved bookmark ID", "expectedCreatedRevision" to "exact bookmark.createdAtRevision"),
        action("reader.bookmark.open", "Open an unchanged saved target without altering notes or answers", "activityId" to "owner reader", "id" to "saved bookmark ID", "expectedCreatedRevision" to "exact bookmark.createdAtRevision"),
        action("reader.bookmarks.show", "Show/hide the reader's saved-bookmark list", "activityId" to "visible active reader", "visible" to "boolean"),
        action("quiz.timer.expand", "Expand/collapse current quiz timer controls without changing counted time", "activityId" to "visible current quiz", "expectedAttempt" to "current attemptNumber", "expectedVersion" to "current timer.version", "expanded" to "boolean"),
        action("quiz.timer.start", "Start/resume the visible current quiz timer; never grade on timeout", "activityId" to "visible current quiz", "expectedAttempt" to "current attemptNumber", "expectedVersion" to "current timer.version"),
        action("quiz.timer.pause", "Pause counted practice time", "activityId" to "visible current quiz", "expectedAttempt" to "current attemptNumber", "expectedVersion" to "current timer.version"),
        action("quiz.timer.reset", "Clear only timer elapsed time when explicitly requested; keep its limit and answers", "activityId" to "visible current quiz", "expectedAttempt" to "current attemptNumber", "expectedVersion" to "current timer.version"),
        action("quiz.timer.limit", "Set total timer duration in seconds, or null for stopwatch; does not start/reset", "activityId" to "visible current quiz", "expectedAttempt" to "current attemptNumber", "expectedVersion" to "current timer.version", "seconds" to "1..86400 or null"),
        action("quiz.restart", "Save current quiz work read-only and start a fresh attempt only when requested", "activityId" to "visible active current quiz", "expectedAttempt" to "current quiz attemptNumber"),
        action("quiz.review_attempt", "View an exact saved attempt/question read-only or return to current attempt", "activityId" to "visible active quiz", "attemptNumber" to "past attempt number or null for current", "questionId" to "saved question ID or null for its saved position; null for current"),
        action("quiz.confidence", "Record only the learner's explicitly reported pre-check confidence, or clear it", "activityId" to "visible active quiz", "questionId" to "current unchecked question ID", "expectedOption" to "current selected answer 0..3", "expectedConfidence" to "current LOW|MEDIUM|HIGH or null", "confidence" to "reported LOW|MEDIUM|HIGH or null to clear"),
        action("learning.filter", "Show saved practice observations in Learning", "filter" to "ALL|MISTAKES|CONFIDENT_MISTAKES|UNCERTAIN_CORRECT|RECALL_REPORTS"),
        action("learning.open_observation", "Open the exact saved question or current recall card without rewriting history", "id" to "current learningObservations ID"),
        action("revision.accept_suggestion", "Apply a native performance-based interval only when requested; do not fabricate evidence", "activityId" to "revisionSuggestions activityId", "questionId" to "suggestion questionId or null", "expectedEvidence" to "exact current suggestion evidenceFingerprint"),
        action("revision.schedule", "Schedule or reschedule one known quiz question or recall card; keep completion history", "activityId" to "existing quiz or recall ID", "questionId" to "existing question ID for quiz; null for recall", "delayDays" to "integer 0..365; elapsed 24-hour periods"),
        action("revision.open", "Resume the exact scheduled activity/question without clearing answers or marking reviewed", "id" to "existing revision item ID"),
        action("revision.complete", "Record the learner's explicitly reported review completion; never infer mastery", "id" to "existing revision item ID", "expectedVersion" to "current revision item version"),
        action("revision.remove", "Remove a scheduled reminder and its completion log only when requested; keep activity answers", "id" to "existing revision item ID", "expectedVersion" to "current revision item version"),
        action("app.navigate", "Open a top-level tab", "destination" to "HOME|WORKSPACE|EXPLORER|SETTINGS|ASSISTANT|REVISION|LEARNING|NOTES|SEARCH"),
        action("app.back", "Return to the parent context"),
        action("subject.open", "Open a subject", "subject" to "PHYSICS|CHEMISTRY|BOTANY|ZOOLOGY"),
        action("chapter.open", "Open the PDF library of a known chapter", "chapterId" to "existing curriculum ID"),
        action("library.search", "Set the current subject's chapter search", "text" to "string, max 200 characters"),
        AppActionContract("library.request_import", "Open the Android PDF picker", mapOf("chapterId" to "existing curriculum ID"), true),
        action("library.open_pdf", "Open an imported original PDF", "pdfId" to "existing library PDF ID"),
        action("library.study_pdf", "Open this PDF's full-screen topic map; explicit resume restores the saved reading cursor", "pdfId" to "existing library PDF ID", "resume" to "false by default; true only for explicit resume"),
        action("source.engineer_page", "Process one imported PDF page in the background", "documentId" to "existing imported document ID", "pageIndex" to "zero-based page index"),
        action("source.review", "Compare original PDF and machine reconstruction without approving it", "documentId" to "existing imported document ID", "pageIndex" to "zero-based page index"),
        action("source.review_region", "Highlight a known bounded element on its original PDF in comparison; null clears", "source" to "SourceAnchor with valid page region, or null in active comparison"),
        action("source.correction.propose", "Propose a separate correction; learner approval required", "id" to "new unique ID", "source" to "known text SourceAnchor", "expectedOriginal" to "exact machine text", "replacement" to "nonblank, max 100000 characters", "expectedApprovedId" to "current approved ID or null"),
        action("source.correction.approve", "Learner-only source approval", "id" to "existing proposed correction ID"),
        action("source.correction.remove", "Learner-only removal of a proposal or approval", "id" to "existing correction ID"),
        action("source.correction.draft", "Private local correction editor; unavailable to AI", "id" to "local draft ID", "draft" to "SourceCorrectionDraft or null to discard"),
        AppActionContract("voice.start", "Start foreground microphone recording; Android permission remains required", emptyMap(), true),
        action("voice.finish", "Stop recording and transcribe using the selected review/automatic-send mode"),
        action("voice.cancel", "Discard active recording or transcription"),
        action("voice.review", "Open the retained transcript and scroll its controls into view"),
        AppActionContract("voice.auto_send", "Legacy mode switch; Speak now always submits after speech ends", mapOf("enabled" to "boolean"), enabled = false),
        action("pdf.page", "Open a zero-based file page", "pageIndex" to "integer within current PDF"),
        action("pdf.next", "Open the next file page"), action("pdf.previous", "Open the previous file page"),
        action("pdf.zoom", "Set bounded PDF zoom", "zoom" to "number 0.5..4"),
        action("pdf.transform", "Set original PDF zoom and pan", "zoom" to "number 0.5..4", "panX" to "finite number -2000..2000", "panY" to "finite number -2000..2000"),
        action("preferences.set", "Set approved reading preferences", "preferences" to "UserPreferences object"),
        action("workspace.command", "Execute a registered workspace tool", "command" to "WorkspaceCommand object"),
        action("workspace.undo_layout", "Undo layout without rolling back answers"),
        action("quiz.select", "Select an answer", "activityId" to "known quiz", "questionId" to "known question", "option" to "integer 0..3"),
        action("quiz.question", "Choose a question in a visible active quiz without changing any answer", "activityId" to "known quiz", "questionId" to "existing question ID in that quiz"),
        action("quiz.submit", "Submit an existing selected answer", "activityId" to "known quiz", "questionId" to "known question"),
        action("recall.response", "Enter the learner's dictated recall response only when explicitly asked", "activityId" to "visible active recall card", "expectedText" to "current exact response, empty initially", "text" to "max 4000 characters"),
        action("recall.reveal", "Reveal the reference answer without grading", "activityId" to "visible active recall card"),
        action("recall.rate", "Record an explicitly reported recall rating; never infer it", "activityId" to "revealed, unrated recall card", "rating" to "FORGOT|PARTIAL|RECALLED"),
        action("recall.restart", "Start another attempt while preserving the reported attempt", "activityId" to "revealed and rated visible recall card"),
        action("reader.position", "Persist the confirmed reading checkpoint", "activityId" to "known reader", "index" to "nonnegative integer", "offset" to "nonnegative integer"),
        action("reader.focus_sentence", "Focus an indexed sentence on this reader's current page", "activityId" to "known reader", "sentenceId" to "sourceContext.sentences ID"),
        action("annotation.create", "Add a separate source-linked note/highlight/underline", "activityId" to "known reader", "id" to "new unique ID",
            "source" to "SourceAnchor on reader page", "sentenceId" to "indexed sentence ID or null for passage", "kind" to "NOTE|HIGHLIGHT|UNDERLINE", "text" to "max 2000 characters; nonblank for NOTE"),
        action("annotation.draft", "Local editor recovery only; unavailable to AI", "activityId" to "known reader", "draftId" to "local draft ID", "draft" to "AnnotationDraft or null to discard"),
        action("annotation.update", "Edit an existing annotation only when asked", "activityId" to "known reader", "id" to "existing annotation ID", "expectedText" to "current exact text", "text" to "replacement max 2000 characters"),
        action("annotation.remove", "Remove an existing annotation only when asked", "activityId" to "known reader", "id" to "existing annotation ID", "expectedText" to "current exact text"),
        action("component.pin", "Pin or unpin a component", "componentId" to "known component", "pinned" to "boolean"),
        action("diagram.transform", "Set bounded source diagram transform", "activityId" to "known diagram", "zoom" to "number 0.5..4", "panX" to "finite number", "panY" to "finite number"),
    )
    fun id(command: AppCommand): String = when (command) {
        is AppCommand.HarnessAnalyzePdf -> "harness.analyze_pdf"
        is AppCommand.HarnessBrowseTopics -> "harness.browse_topics"
        is AppCommand.HarnessOpenTopic -> "harness.open_topic"; is AppCommand.HarnessInspectTopic -> "harness.inspect_topic"
        is AppCommand.HarnessInspectPage -> "harness.inspect_page"
        is AppCommand.HarnessIncludeTopics -> "harness.include_topics"; is AppCommand.HarnessRemoveSource -> "harness.remove_source"
        is AppCommand.AnalyzeStudy -> "study.analyze"; is AppCommand.CancelStudyWork -> "study.cancel"
        is AppCommand.SelectStudyTopics -> "study.select_topics"; is AppCommand.BeginStudy -> "study.begin"
        is AppCommand.ShowStudyTopics -> "study.topics"; is AppCommand.GuideStudy -> "study.guide"
        is AppCommand.FinishStudyLine -> "study.finished_line"; is AppCommand.StudyPage -> "study.page"
        is AppCommand.StudySentence -> "study.sentence"
        is AppCommand.SearchStudy -> "search.query"
        is AppCommand.SearchPage -> "search.page"
        is AppCommand.OpenSearchResult -> "search.open"
        is AppCommand.SetNotesFilter -> "notes.filter"
        is AppCommand.SearchNotes -> "notes.search"
        is AppCommand.OpenSavedAnnotation -> "notes.open"
        is AppCommand.BeginSavedNoteEdit -> "notes.edit_note"
        is AppCommand.EditSavedDraft -> "notes.edit_draft"
        is AppCommand.CommitSavedDraft -> "notes.commit_draft"
        is AppCommand.DiscardSavedDraft -> "notes.discard_draft"
        AppCommand.CheckSourceCache -> "source.cache.check"
        is AppCommand.ConfirmSourceCacheRepair -> "source.cache.confirm_repair"
        AppCommand.DismissSourceCacheRepair -> "source.cache.dismiss"
        is AppCommand.ShowReadingCheckpoints -> "reader.checkpoints.show"
        is AppCommand.OpenReadingConfirmation -> "reader.checkpoints.open"
        is AppCommand.ConfirmReading -> "reader.confirm_read"
        is AppCommand.ClearReadingConfirmation -> "reader.clear_confirmation"
        is AppCommand.AddReadingBookmark -> "reader.bookmark.add"
        is AppCommand.RemoveReadingBookmark -> "reader.bookmark.remove"
        is AppCommand.OpenReadingBookmark -> "reader.bookmark.open"
        is AppCommand.ShowReadingBookmarks -> "reader.bookmarks.show"
        is AppCommand.SetPracticeTimerExpanded -> "quiz.timer.expand"
        is AppCommand.StartPracticeTimer -> "quiz.timer.start"
        is AppCommand.PausePracticeTimer -> "quiz.timer.pause"
        is AppCommand.ResetPracticeTimer -> "quiz.timer.reset"
        is AppCommand.SetPracticeTimerLimit -> "quiz.timer.limit"
        is AppCommand.RestartQuiz -> "quiz.restart"
        is AppCommand.ReviewQuizAttempt -> "quiz.review_attempt"
        is AppCommand.SetAnswerConfidence -> "quiz.confidence"
        is AppCommand.SetLearningFilter -> "learning.filter"
        is AppCommand.OpenLearningObservation -> "learning.open_observation"
        is AppCommand.AcceptRevisionSuggestion -> "revision.accept_suggestion"
        is AppCommand.ScheduleRevision -> "revision.schedule"
        is AppCommand.OpenRevision -> "revision.open"
        is AppCommand.CompleteRevision -> "revision.complete"
        is AppCommand.RemoveRevision -> "revision.remove"
        is AppCommand.RecallResponse -> "recall.response"
        is AppCommand.RevealRecall -> "recall.reveal"
        is AppCommand.RateRecall -> "recall.rate"
        is AppCommand.RestartRecall -> "recall.restart"
        is AppCommand.ReviewRegion -> "source.review_region"
        is AppCommand.SaveCorrectionDraft -> "source.correction.draft"
        is AppCommand.ProposeCorrection -> "source.correction.propose"
        is AppCommand.ApproveCorrection -> "source.correction.approve"
        is AppCommand.RemoveCorrection -> "source.correction.remove"
        is AppCommand.ReviewPage -> "source.review"
        is AppCommand.QuizQuestion -> "quiz.question"
        is AppCommand.SaveAnnotationDraft -> "annotation.draft"
        is AppCommand.CreateAnnotation -> "annotation.create"
        is AppCommand.UpdateAnnotation -> "annotation.update"
        is AppCommand.RemoveAnnotation -> "annotation.remove"
        is AppCommand.Navigate -> "app.navigate"; AppCommand.Back -> "app.back"
        is AppCommand.OpenSubject -> "subject.open"; is AppCommand.OpenChapterLibrary -> "chapter.open"
        is AppCommand.SearchChapters -> "library.search"; is AppCommand.RequestPdfImport -> "library.request_import"
        is AppCommand.OpenPdf -> "library.open_pdf"; is AppCommand.StudyPdf -> "library.study_pdf"
        is AppCommand.EngineerPage -> "source.engineer_page"
        AppCommand.StartVoice -> "voice.start"; AppCommand.FinishVoice -> "voice.finish"; AppCommand.CancelVoice -> "voice.cancel"
        AppCommand.ReviewVoice -> "voice.review"
        is AppCommand.VoiceAutoSend -> "voice.auto_send"
        is AppCommand.PdfPage -> "pdf.page"; AppCommand.NextPdfPage -> "pdf.next"; AppCommand.PreviousPdfPage -> "pdf.previous"
        is AppCommand.PdfZoom -> "pdf.zoom"; is AppCommand.SetPreferences -> "preferences.set"
        is AppCommand.PdfTransform -> "pdf.transform"
        is AppCommand.WorkspaceAction -> "workspace.command"; AppCommand.UndoLayout -> "workspace.undo_layout"
        is AppCommand.SelectAnswer -> "quiz.select"; is AppCommand.SubmitAnswer -> "quiz.submit"
        is AppCommand.ReadingPosition -> "reader.position"; is AppCommand.FocusSentence -> "reader.focus_sentence"
        is AppCommand.Pin -> "component.pin"; is AppCommand.Transform -> "diagram.transform"
    }
    private fun action(id: String, description: String, vararg args: Pair<String, String>) = AppActionContract(id, description, args.toMap())
}
