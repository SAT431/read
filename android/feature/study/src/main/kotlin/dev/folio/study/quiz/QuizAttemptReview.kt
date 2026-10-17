package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.practice.QuizProgress
import dev.folio.ui.component.ComponentRenderContext
import dev.folio.ui.practice.confidenceLabel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuizAttemptReview(context: ComponentRenderContext, quiz: ActivityData.Quiz) {
    val selected = requireNotNull(quiz.review)
    val saved = quiz.pastAttempts.single { it.number == selected.attemptNumber }
    val index = saved.questions.indexOfFirst { it.id == selected.questionId }
    val question = saved.questions[index]
    val checked = question.id in saved.submitted
    val progress = QuizProgress.from(ActivityData.Quiz(saved.questions, answers = saved.answers, submitted = saved.submitted))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Saved attempt ${saved.number} · read-only", Modifier.testTag("quiz-history-heading"), style = MaterialTheme.typography.titleLarge)
        Text("Saved: ${DateFormat.getDateTimeInstance().format(Date(saved.archivedAtEpochMillis))}")
        if (saved.archivedByAssistant) Text("Attempt saved by assistant at your request", style = MaterialTheme.typography.labelSmall)
        Text(saved.timer?.let { "Recorded practice time: ${dev.folio.ui.practice.formatPracticeTime(it.elapsedMillis)} · ${it.status.name.lowercase()}" }
            ?: "Practice time not recorded")
        Text("Checked ${progress.checked} of ${progress.total} · ${progress.correct} correct · ${progress.mistakes.size} incorrect")
        OutlinedButton({ context.onInteraction(LearningInteraction.ReviewQuizAttempt(context.activity.id, null, null)) },
            Modifier.testTag("quiz-return-current")) { Text("Return to current attempt ${quiz.attemptNumber}") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            saved.questions.forEachIndexed { i, q -> FilterChip(selected = q.id == question.id,
                onClick = { context.onInteraction(LearningInteraction.ReviewQuizAttempt(context.activity.id, saved.number, q.id)) },
                label = { Text("Question ${i + 1}") }, modifier = Modifier.testTag("quiz-history-question-${q.id}")) }
        }
        Text("Results use this saved attempt's question and answer key.", style = MaterialTheme.typography.bodySmall)
        if (question.sources.any { context.catalog.page(it.documentId, it.pageIndex)?.corrections?.isNotEmpty() == true })
            Text("This source includes approved corrections. Compare the saved answer key with the source.")
        SelectionContainer { Text(question.prompt, style = MaterialTheme.typography.headlineSmall) }
        question.options.forEachIndexed { option, text -> AnswerOption("${('A'.code + option).toChar()}. $text", saved.answers[question.id] == option,
            checked, option == question.correctOption, "quiz-history-option-${question.id}-$option", enabled = false) {} }
        if (checked) QuizFeedback(question, saved.answers[question.id]) else Text("This choice was not checked in the saved attempt.")
        Text("Confidence: ${saved.confidence[question.id]?.level?.let(::confidenceLabel) ?: "not recorded"}")
        if (saved.confidence[question.id]?.byAssistant == true) Text("Reported confidence entered by assistant")
        saved.checks[question.id]?.let { check ->
            Text("Checked: ${DateFormat.getDateTimeInstance().format(Date(check.checkedAtEpochMillis))}")
            if (check.byAssistant) Text("Answer check requested by assistant")
            if (check.selectionByAssistant == true) Text("Answer selection entered by assistant")
        }
        QuestionSourceReferences(context.activity.id, question, context.catalog, context.onCommands)
    }
}
