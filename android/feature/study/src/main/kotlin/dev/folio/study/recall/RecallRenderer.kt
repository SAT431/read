package dev.folio.study.recall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.primitive.FolioActionButton
import dev.folio.model.activity.ActivityData
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.study.source.StudySourceReferences
import dev.folio.ui.component.*

class RecallRenderer : RegisteredRenderer {
    override val kind = ComponentKind.RECALL
    @Composable override fun Render(context: ComponentRenderContext) {
        val recall = context.activity.data as ActivityData.Recall
        val id = context.activity.id
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Active recall · attempt ${recall.pastAttempts.size + 1}", style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(recall.prompt, style = MaterialTheme.typography.headlineSmall) }
            RecallAnswerEditor(id, recall, context.onInteraction)
            if (recall.revealed) {
                Text("Reference answer · compare with source", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(recall.referenceAnswer, Modifier.testTag("recall-reference-$id")) }
                RecallRatingPanel(id, recall, context.onInteraction)
                if (recall.rating != null) FolioActionButton("Try again", {
                    context.onInteraction(LearningInteraction.RestartRecall(id))
                }, enabled = recall.pastAttempts.size < 100)
            } else FolioActionButton("Reveal reference answer", { context.onInteraction(LearningInteraction.RevealRecall(id)) })
            if (recall.sources.any { context.catalog.page(it.documentId, it.pageIndex)?.corrections?.isNotEmpty() == true })
                Text("This source includes approved corrections. Check the reference answer against it.")
            if (recall.pastAttempts.isNotEmpty()) {
                Text("Recent reported attempts (last ${minOf(5, recall.pastAttempts.size)} of ${recall.pastAttempts.size})")
                recall.pastAttempts.takeLast(5).asReversed().forEach { attempt ->
                    Text("${attempt.rating.label()} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(attempt.ratedAtEpochMillis))}")
                    Text(attempt.response.ifBlank { "No written response" })
                    if (attempt.responseByAssistant || attempt.ratingByAssistant) Text("Includes an assistant-entered response or rating.")
                }
            }
            dev.folio.study.revision.RevisionSchedulePanel(id, null, context.revisionItems, context.onInteraction,
                dev.folio.runtime.practice.RevisionSuggestions.forActivity(context.activity, null))
            StudySourceReferences(id, recall.sources, context.catalog, context.onCommands, "recall-source-$id")
        }
    }
}
