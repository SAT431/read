package dev.folio.study.quiz

import androidx.compose.runtime.Composable
import dev.folio.model.activity.Question
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.study.source.StudySourceReferences

@Composable
internal fun QuestionSourceReferences(activityId: String, question: Question, catalog: SourceCatalog,
    onCommands: (List<WorkspaceCommand>) -> Unit) {
    StudySourceReferences(activityId, question.sources, catalog, onCommands, "quiz-source-${question.id}")
}
