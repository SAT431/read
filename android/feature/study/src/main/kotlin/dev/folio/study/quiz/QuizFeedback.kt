package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.Question

@Composable
internal fun QuizFeedback(question: Question, selected: Int?) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (selected == question.correctOption) "Correct answer" else "Incorrect answer", style = MaterialTheme.typography.titleMedium)
            Text("Correct option: ${('A'.code + question.correctOption).toChar()}. ${question.options[question.correctOption]}")
            Text(question.explanation)
        }
    }
}
