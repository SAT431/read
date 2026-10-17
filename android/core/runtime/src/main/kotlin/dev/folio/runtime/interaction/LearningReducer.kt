package dev.folio.runtime.interaction

import dev.folio.model.activity.ActivityData
import dev.folio.model.activity.ReadingPosition
import dev.folio.model.workspace.Workspace

class LearningReducer(private val catalog: dev.folio.model.port.SourceCatalog? = null, private val now: () -> Long = System::currentTimeMillis) {
    fun reduce(state: Workspace, event: LearningInteraction,
        actor: dev.folio.model.command.CommandActor = dev.folio.model.command.CommandActor.USER): Workspace {
        if (event is LearningInteraction.ShowReadingCheckpoints || event is LearningInteraction.OpenReadingConfirmation ||
            event is LearningInteraction.ConfirmReading || event is LearningInteraction.ClearReadingConfirmation)
            return dev.folio.runtime.reading.ReadingConfirmationReducer(catalog ?: error("Reading source unavailable"), now).apply(state, event, actor)
        if (event is LearningInteraction.AddReadingBookmark || event is LearningInteraction.RemoveReadingBookmark ||
            event is LearningInteraction.OpenReadingBookmark || event is LearningInteraction.ShowReadingBookmarks)
            return dev.folio.runtime.reading.ReadingBookmarkReducer(catalog ?: error("Bookmark source unavailable"), now).apply(state, event, actor)
        if (event is LearningInteraction.SetPracticeTimerExpanded || event is LearningInteraction.StartPracticeTimer || event is LearningInteraction.PausePracticeTimer ||
            event is LearningInteraction.ResetPracticeTimer || event is LearningInteraction.SetPracticeTimerLimit)
            return dev.folio.runtime.practice.PracticeTimerReducer().apply(state, event, actor)
        if (event is LearningInteraction.RestartQuiz || event is LearningInteraction.ReviewQuizAttempt)
            return dev.folio.runtime.practice.QuizHistoryReducer(catalog, now).apply(state, event, actor)
        if (event is LearningInteraction.SetAnswerConfidence) return dev.folio.runtime.practice.QuizConfidenceReducer().apply(state, event, actor)
        if (event is LearningInteraction.AcceptRevisionSuggestion) return dev.folio.runtime.practice.RevisionQueueReducer(
            catalog ?: error("Revision source unavailable")).apply(state,
            dev.folio.model.command.AppCommand.AcceptRevisionSuggestion(event.activityId, event.questionId, event.expectedEvidence), actor)
            .copy(revision = state.revision + 1)
        if (event is LearningInteraction.ScheduleRevision) return dev.folio.runtime.practice.RevisionQueueReducer(
            catalog ?: error("Revision source unavailable")).apply(state,
            dev.folio.model.command.AppCommand.ScheduleRevision(event.activityId, event.questionId, event.delayDays), actor)
            .copy(revision = state.revision + 1)
        if (event is LearningInteraction.RecallResponse || event is LearningInteraction.RevealRecall ||
            event is LearningInteraction.RateRecall || event is LearningInteraction.RestartRecall)
            return dev.folio.runtime.practice.RecallReducer().apply(state, event, actor)
        if (event is LearningInteraction.QuizQuestion)
            return dev.folio.runtime.practice.QuizNavigation(catalog ?: error("Quiz source unavailable")).apply(state, event)
        if (event is LearningInteraction.SaveAnnotationDraft)
            return AnnotationDraftReducer(catalog ?: error("Draft source unavailable")).apply(state, event, actor)
        if (event is LearningInteraction.CreateAnnotation || event is LearningInteraction.UpdateAnnotation || event is LearningInteraction.RemoveAnnotation)
            return SourceAnnotationReducer(catalog ?: error("Annotation source unavailable")).apply(state, event, actor)
        if (event is LearningInteraction.FocusSentence) return dev.folio.runtime.reading.ReaderSentenceFocus(
            catalog ?: error("Sentence source unavailable")).apply(state, event)
        if (event is LearningInteraction.PinComponent) {
            val component = state.components[event.componentId] ?: error("Unknown component")
            return state.copy(revision = state.revision + 1,
                components = state.components + (component.id to component.copy(pinned = event.pinned)))
        }
        val id = when (event) {
            is LearningInteraction.SelectAnswer -> event.activityId
            is LearningInteraction.SubmitAnswer -> event.activityId
            is LearningInteraction.FocusSentence -> event.activityId
            is LearningInteraction.SetReadingPosition -> event.activityId
            is LearningInteraction.TransformDiagram -> event.activityId
            else -> error("Unsupported interaction")
        }
        val activity = state.activities[id] ?: error("Unknown activity")
        val updated = when (event) {
            is LearningInteraction.SelectAnswer -> {
                val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
                require(quiz.review == null) { "Saved attempts are read-only; return to the current attempt" }
                val question = quiz.questions.firstOrNull { it.id == event.questionId } ?: error("Unknown question")
                require(event.option in question.options.indices && question.id !in quiz.submitted)
                quiz.copy(answers = quiz.answers + (question.id to event.option),
                    confidence = if (quiz.answers[question.id] == event.option) quiz.confidence else quiz.confidence - question.id,
                    selectionActors = quiz.selectionActors + (question.id to actor))
            }
            is LearningInteraction.SubmitAnswer -> {
                val quiz = activity.data as? ActivityData.Quiz ?: error("Not a quiz")
                require(quiz.review == null) { "Saved attempts are read-only; return to the current attempt" }
                require(event.questionId in quiz.answers && event.questionId !in quiz.submitted) { "Choose an answer before checking; checked answers are locked" }
                val time = now(); require(time >= 0)
                quiz.copy(submitted = quiz.submitted + event.questionId, checks = quiz.checks + (event.questionId to dev.folio.model.activity.QuizCheck(
                    time, actor == dev.folio.model.command.CommandActor.AI, quiz.selectionActors[event.questionId]?.let { it == dev.folio.model.command.CommandActor.AI })))
            }
            is LearningInteraction.FocusSentence -> {
                error("Sentence focus is validated before reduction")
            }
            is LearningInteraction.SetReadingPosition -> {
                val reader = activity.data as? ActivityData.Reader ?: error("Not a reader")
                require(event.index >= 0 && event.offset >= 0)
                val pageKey = "${activity.source.documentId}:${activity.source.pageIndex}"
                reader.copy(scrollIndex = event.index, scrollOffset = event.offset,
                    pagePositions = reader.pagePositions + (pageKey to ReadingPosition(event.index, event.offset)))
            }
            is LearningInteraction.TransformDiagram -> {
                val diagram = activity.data as? ActivityData.Diagram ?: error("Not a diagram")
                require(event.zoom.isFinite() && event.panX.isFinite() && event.panY.isFinite())
                diagram.copy(zoom = event.zoom.coerceIn(.5f, 4f),
                    panX = event.panX.coerceIn(-2000f, 2000f), panY = event.panY.coerceIn(-2000f, 2000f))
            }
            else -> error("Unsupported interaction")
        }
        return state.copy(revision = state.revision + 1, activities = state.activities + (id to activity.copy(data = updated)))
    }
}
