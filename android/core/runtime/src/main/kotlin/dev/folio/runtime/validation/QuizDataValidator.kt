package dev.folio.runtime.validation

import dev.folio.model.activity.*
import dev.folio.model.command.CommandActor
import dev.folio.model.port.SourceCatalog

/** Creation requires live sources; persisted questions retain structure when their source is unavailable. */
object QuizDataValidator {
    fun validate(data: ActivityData.Quiz, catalog: SourceCatalog?, requireAvailableSources: Boolean = true) {
        dev.folio.runtime.practice.PracticeTimerRules.validate(data.timer)
        validateAttempt(data.questions, data.index, data.answers, data.submitted, data.confidence, data.checks, data.selectionActors)
        if (requireAvailableSources && catalog != null) require(data.questions.all { it.sources.all(catalog::contains) }) { "Question has unknown source" }
        require(data.pastAttempts.size <= 100 && data.attemptNumber == data.pastAttempts.size + 1)
        data.pastAttempts.forEachIndexed { index, saved ->
            saved.timer?.let { dev.folio.runtime.practice.PracticeTimerRules.validate(it, archived = true) }
            require(saved.number == index + 1 && saved.archivedAtEpochMillis >= 0)
            validateAttempt(saved.questions, saved.index, saved.answers, saved.submitted, saved.confidence, saved.checks, saved.selectionActors)
        }
        data.review?.let { selected ->
            require(data.pastAttempts.any { it.number == selected.attemptNumber && it.questions.any { q -> q.id == selected.questionId } })
        }
        val all = data.questions + data.pastAttempts.flatMap { it.questions }
        require(all.sumOf { q -> q.prompt.length.toLong() + q.explanation.length + q.options.sumOf { it.length } } <= 1_000_000) { "Quiz question history storage limit reached" }
    }
    private fun validateAttempt(questions: List<Question>, index: Int, answers: Map<String, Int>, submitted: Set<String>,
        confidence: Map<String, AnswerConfidence>, checks: Map<String, QuizCheck>, selectionActors: Map<String, CommandActor>) {
        require(questions.size in 1..20 && index in questions.indices)
        val ids = questions.map { it.id }.toSet(); require(ids.size == questions.size)
        questions.forEach { q ->
            require(q.id.isNotBlank() && q.prompt.isNotBlank() && q.options.size == 4 && q.correctOption in 0..3)
            require(q.options.all { it.isNotBlank() } && q.sources.isNotEmpty())
            q.sources.forEach(dev.folio.runtime.source.SavedSourceReferences::validate)
        }
        require(answers.all { (id, answer) -> id in ids && answer in 0..3 })
        require(submitted.all { it in answers })
        require(confidence.size <= ids.size && confidence.all { (id, report) ->
            id in ids && report.recordedAtEpochMillis >= 0 && report.selectedOption in 0..3 && answers[id] == report.selectedOption
        })
        require(checks.all { (id, check) -> id in submitted && check.checkedAtEpochMillis >= 0 &&
            check.selectionByAssistant == selectionActors[id]?.let { it == CommandActor.AI } })
        require(selectionActors.keys.all { it in answers })
    }
}
