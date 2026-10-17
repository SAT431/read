package dev.folio.study.recall

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.*
import dev.folio.runtime.interaction.LearningInteraction

internal fun RecallRating.label() = when (this) {
    RecallRating.FORGOT -> "Forgot"
    RecallRating.PARTIAL -> "Partly recalled"
    RecallRating.RECALLED -> "Recalled"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecallRatingPanel(activityId: String, recall: ActivityData.Recall, onInteraction: (LearningInteraction) -> Unit) {
    Text("How much did you recall?", style = MaterialTheme.typography.titleSmall)
    Text("Your reported recall, not an AI score or verified mastery.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecallRating.entries.forEach { rating ->
            FilterChip(recall.rating == rating, { onInteraction(LearningInteraction.RateRecall(activityId, rating)) },
                { Text(rating.label()) }, Modifier.testTag("recall-rating-${rating.name}"), enabled = recall.rating == null)
        }
    }
    if (recall.ratingByAssistant) Text("Rating entered through assistant.", style = MaterialTheme.typography.labelSmall)
}
