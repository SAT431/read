package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData

@Composable
internal fun QuizNavigationBar(quiz: ActivityData.Quiz, onQuestion: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ onQuestion(quiz.questions[quiz.index - 1].id) }, enabled = quiz.index > 0) { Text("Previous question") }
        TextButton({ onQuestion(quiz.questions[quiz.index + 1].id) }, enabled = quiz.index < quiz.questions.lastIndex) { Text("Next question") }
    }
}
