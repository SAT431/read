package dev.folio.study.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.runtime.practice.QuizProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PracticeSummaryCard(quiz: ActivityData.Quiz, onQuestion: (String) -> Unit) {
    val progress = QuizProgress.from(quiz)
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (progress.checked == progress.total) "Practice complete" else "Practice progress", style = MaterialTheme.typography.titleMedium)
            Text("Checked ${progress.checked} of ${progress.total} · ${progress.correct} correct · ${progress.mistakes.size} incorrect · ${progress.unchecked.size} unchecked")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ progress.nextAfter(quiz.index, progress.mistakes)?.let { onQuestion(quiz.questions[it].id) } },
                    enabled = progress.mistakes.isNotEmpty()) { Text("Review next mistake") }
                TextButton({ progress.nextAfter(quiz.index, progress.unchecked)?.let { onQuestion(quiz.questions[it].id) } },
                    enabled = progress.unchecked.isNotEmpty()) { Text("Next unchecked") }
            }
        }
    }
}
