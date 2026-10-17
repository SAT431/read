package dev.folio.study.registry

import dev.folio.study.reader.ReaderRenderer
import dev.folio.study.quiz.QuizRenderer
import dev.folio.study.explanation.ExplanationRenderer
import dev.folio.study.comparison.ComparisonRenderer
import dev.folio.study.diagram.DiagramRenderer
import dev.folio.study.recall.RecallRenderer
import dev.folio.ui.component.RegisteredRenderer

object StudyRenderers {
    fun all(): List<RegisteredRenderer> = listOf(ReaderRenderer(), QuizRenderer(), ExplanationRenderer(), ComparisonRenderer(), DiagramRenderer(), RecallRenderer())
}
