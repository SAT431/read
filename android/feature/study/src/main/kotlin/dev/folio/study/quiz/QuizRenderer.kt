package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.component.*

class QuizRenderer : RegisteredRenderer {
    override val kind = ComponentKind.QUIZ
    @Composable override fun Render(context: ComponentRenderContext) {
        val quiz = context.activity.data as ActivityData.Quiz
        if (quiz.review != null) { QuizAttemptReview(context, quiz); return }
        val question = quiz.questions[quiz.index]
        val submitted = question.id in quiz.submitted
        val onQuestion: (String) -> Unit = { context.onInteraction(LearningInteraction.QuizQuestion(context.activity.id, it)) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Attempt ${quiz.attemptNumber}", style = MaterialTheme.typography.labelLarge)
            Text("Question ${quiz.index + 1} of ${quiz.questions.size}", style = MaterialTheme.typography.labelSmall)
            PracticeTimerPanel(context.activity.id, quiz, context.onInteraction)
            QuestionProgressRail(quiz, onQuestion)
            QuizNavigationBar(quiz, onQuestion)
            Text(question.prompt, style = MaterialTheme.typography.headlineMedium)
            if (question.sources.any { context.catalog.page(it.documentId, it.pageIndex)?.corrections?.isNotEmpty() == true })
                Text("This source includes approved corrections. Compare the question with its supporting source.")
            question.options.forEachIndexed { index, text ->
                AnswerOption("${('A'.code + index).toChar()}. $text", quiz.answers[question.id] == index, submitted,
                    index == question.correctOption, "quiz-option-${question.id}-$index") {
                    context.onInteraction(LearningInteraction.SelectAnswer(context.activity.id, question.id, index))
                }
            }
            AnswerConfidencePanel(context.activity.id, question.id, quiz, context.onInteraction)
            if (submitted) QuizFeedback(question, quiz.answers[question.id])
            else FolioActionButton("Check answer", {
                context.onInteraction(LearningInteraction.SubmitAnswer(context.activity.id, question.id))
            }, enabled = question.id in quiz.answers)
            dev.folio.study.revision.RevisionSchedulePanel(context.activity.id, question.id, context.revisionItems, context.onInteraction,
                dev.folio.runtime.practice.RevisionSuggestions.forActivity(context.activity, question.id))
            PracticeSummaryCard(quiz, onQuestion)
            QuizAttemptControls(context.activity.id, quiz, context.onInteraction)
            QuestionSourceReferences(context.activity.id, question, context.catalog, context.onCommands)
        }
    }
}
