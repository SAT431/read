package dev.folio.library.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.workspace.*
import dev.folio.runtime.practice.LearningMemory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearningOverviewScreen(state: Workspace, catalog: SourceCatalog, dispatch: (AppCommand) -> Unit) {
    val observations = remember(state.activities) { LearningMemory.observations(state) }
    val summary = remember(observations) { LearningMemory.summary(observations) }
    val filter = state.navigation.learningFilter
    val shown = observations.filter { LearningMemory.matches(it, filter) }
    LazyColumn(Modifier.fillMaxSize().testTag("learning-memory"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("heading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dev.folio.ui.control.SemanticButton("learning-open-notes", "Saved notes and drafts", AppCommand.Navigate(AppDestination.NOTES), dispatch)
                Text("Learning memory", style = MaterialTheme.typography.headlineMedium)
                Text("Saved checks and reported recall. Use these observations to choose what to revisit.")
                Text("${summary.checked} checked · ${summary.correct} correct · ${summary.incorrect} incorrect")
                Text("${summary.confidentMistakes} confident mistakes · ${summary.uncertainCorrect} correct while unsure · ${summary.recallReports} recall reports")
                Text("Correctness follows each saved question's answer key. These records do not measure mastery.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LearningFilter.entries.forEach { choice -> FilterChip(selected = choice == filter,
                        onClick = { dispatch(AppCommand.SetLearningFilter(choice)) },
                        label = { Text(filterLabel(choice)) }, modifier = Modifier.testTag("learning-filter-${choice.name}")) }
                }
                Text("${shown.size} saved observation${if (shown.size == 1) "" else "s"}", Modifier.testTag("learning-count"))
            }
        }
        if (shown.isEmpty()) item("empty") {
            Text(if (observations.isEmpty()) "Check a quiz answer or report a recall rating to start your learning memory."
                else "No saved observations match this filter.")
        }
        items(shown, key = { it.id }) { observation ->
            val currentNumber = (state.activities[observation.activityId]?.data as? dev.folio.model.activity.ActivityData.Quiz)?.attemptNumber
            LearningObservationCard(observation, catalog, dispatch,
                historicalQuiz = observation.questionId != null && observation.attemptNumber != null && observation.attemptNumber != currentNumber)
        }
    }
}

private fun filterLabel(filter: LearningFilter): String = when (filter) {
    LearningFilter.ALL -> "All"
    LearningFilter.MISTAKES -> "Mistakes"
    LearningFilter.CONFIDENT_MISTAKES -> "Confident mistakes"
    LearningFilter.UNCERTAIN_CORRECT -> "Correct while unsure"
    LearningFilter.RECALL_REPORTS -> "Recall reports"
}
