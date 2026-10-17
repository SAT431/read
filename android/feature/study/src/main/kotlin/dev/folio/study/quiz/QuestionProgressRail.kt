package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionProgressRail(quiz: ActivityData.Quiz, onQuestion: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        quiz.questions.forEachIndexed { index, question ->
            val status = when {
                question.id in quiz.submitted -> if (quiz.answers[question.id] == question.correctOption) "correct" else "incorrect"
                question.id in quiz.answers -> "selected"
                else -> "unchecked"
            }
            FilterChip(selected = index == quiz.index, onClick = { onQuestion(question.id) },
                label = { Text("Q${index + 1} · $status") }, modifier = Modifier.testTag("quiz-question-${question.id}"))
        }
    }
}
