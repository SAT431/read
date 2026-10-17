package dev.folio.runtime.study

import dev.folio.model.workspace.*
import java.util.Locale

data class GuidedVoiceTarget(val pdfId: String, val version: Long, val sentenceId: String)
object GuidedStudyVoice {
    fun isConfirmation(text: String) = text.trim().lowercase(Locale.ROOT).trimEnd('.', '!', '?') in
        setOf("yes", "yes finished", "i finished", "finished", "done", "yes i finished")
    fun target(state: Workspace): GuidedVoiceTarget? {
        if (state.navigation.destination != AppDestination.STUDY) return null
        val plan = state.studyPlans[state.navigation.pdfId] ?: return null
        val sentence = plan.sentenceId ?: return null
        return if (plan.guided) GuidedVoiceTarget(plan.pdfId, plan.version, sentence) else null
    }
    fun maySubmit(text: String, captured: GuidedVoiceTarget?, current: Workspace) = !isConfirmation(text) || captured == target(current)
}
