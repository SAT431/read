package dev.folio.runtime.assistant

import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.library.SubjectId
import dev.folio.model.port.CurriculumCatalog
import dev.folio.model.workspace.*
import java.text.Normalizer
import java.util.Locale

data class LocalNavigationIntent(val commands: List<AppCommand>, val description: String)

/** Explicit navigation shortcuts only. Unknown/ambiguous language still goes to Gemini. */
class LocalNavigationCommands(private val curriculum: CurriculumCatalog) {
    fun resolve(text: String, state: Workspace, pdfs: List<LibraryPdf>): LocalNavigationIntent? {
        var phrase = normalize(text).trimEnd('.', '!', '?')
        for (prefix in listOf("please ", "can you ", "could you ", "would you ", "i want to ", "i would like to ", "once ", "please ")) phrase = phrase.removePrefix(prefix)
        phrase = phrase.removeSuffix(" please").trim().trimEnd(',')
        if (state.navigation.destination == AppDestination.STUDY && dev.folio.runtime.study.GuidedStudyVoice.isConfirmation(phrase)) {
            val plan = state.studyPlans[state.navigation.pdfId]
            if (plan?.guided == true && plan.sentenceId != null) return intent(
                AppCommand.FinishStudyLine(plan.pdfId, plan.version, requireNotNull(plan.sentenceId)), "Recorded your highlighted line as read and advanced the guide.")
        }
        val tabs = mapOf("open search" to AppDestination.SEARCH, "open notes" to AppDestination.NOTES, "open saved notes" to AppDestination.NOTES, "open home" to AppDestination.HOME, "go home" to AppDestination.HOME,
            "open workspace" to AppDestination.WORKSPACE, "open settings" to AppDestination.SETTINGS,
            "open assistant" to AppDestination.ASSISTANT, "open learning" to AppDestination.LEARNING, "open learning memory" to AppDestination.LEARNING, "open revision" to AppDestination.REVISION, "open revision queue" to AppDestination.REVISION)
        tabs[phrase]?.let { return intent(AppCommand.Navigate(it), "Opened ${it.name.lowercase()}.") }
        val pdfContext = AssistantSourceSelection.viewingPdf(state)
        val workspaceContext = state.navigation.destination == AppDestination.WORKSPACE ||
            (state.navigation.destination == AppDestination.ASSISTANT && state.navigation.assistantOrigin == AppDestination.WORKSPACE)
        val currentPdf = when {
            pdfContext -> pdfs.singleOrNull { it.id == state.navigation.pdfId }
            workspaceContext -> pdfs.singleOrNull { it.document.id == state.context.source?.documentId }
            state.navigation.chapterId != null -> pdfs.filter { it.chapterId == state.navigation.chapterId }.singleOrNull()
            else -> null
        }
        val step = when (phrase) {
            "next page", "go next page", "go to next page", "go to the next page", "open next page", "open the next page" -> 1
            "previous page", "go previous page", "go to previous page", "go to the previous page", "open previous page", "open the previous page" -> -1
            else -> 0
        }
        if (step != 0) {
            val label = if (step > 0) "next" else "previous"
            if (pdfContext && currentPdf != null) return intent(if (step > 0) AppCommand.NextPdfPage else AppCommand.PreviousPdfPage, "Opened $label file page.")
            if (workspaceContext && state.context.source != null) return LocalNavigationIntent(listOf(
                AppCommand.WorkspaceAction(if (step > 0) WorkspaceCommand.NextPage else WorkspaceCommand.PreviousPage),
                AppCommand.Navigate(AppDestination.WORKSPACE)), "Opened $label study page.")
            return null
        }
        if (phrase in setOf("open that pdf", "open this pdf", "open the pdf", "open pdf"))
            return currentPdf?.let { intent(AppCommand.OpenPdf(it.id), "Opened ${it.document.title}.") }
        if (phrase in setOf("study that pdf", "study this pdf", "study the pdf"))
            return currentPdf?.let { intent(AppCommand.StudyPdf(it.id), "Opened ${it.document.title} for study.") }
        val filePage = Regex("(?:open |go to )?file page ([1-9][0-9]{0,5})").matchEntire(phrase)?.groupValues?.get(1)?.toInt()
        if (filePage != null && pdfContext && currentPdf != null)
            return intent(AppCommand.PdfPage(filePage - 1), "Opened file page $filePage.")
        if (!phrase.startsWith("open ")) return null
        val target = phrase.removePrefix("open ")
        val candidates = mutableListOf<LocalNavigationIntent>()
        curriculum.subjects().filter { normalize(it.title) == target }.forEach {
            candidates.add(intent(AppCommand.OpenSubject(it.id), "Opened ${it.title}."))
        }
        val chapterTarget = target.removeSuffix(" chapter")
        val newtonAlias = chapterTarget in setOf("newtons laws of motion", "newton's laws of motion", "newton’s laws of motion")
        curriculum.chapters().filter { normalize(it.title) == chapterTarget ||
            (newtonAlias && it.subject == SubjectId.PHYSICS && normalize(it.title) == "laws of motion") }.forEach {
            candidates.add(intent(AppCommand.OpenChapterLibrary(it.id), "Opened ${it.title} library."))
        }
        val matchingPdfs = pdfs.filter { normalize(it.document.title) == target || normalize(it.document.title).removeSuffix(".pdf") == target }
        val scoped = matchingPdfs.filter { it.chapterId == state.navigation.chapterId }
        (if (scoped.isNotEmpty()) scoped else matchingPdfs).forEach {
            candidates.add(intent(AppCommand.OpenPdf(it.id), "Opened ${it.document.title}."))
        }
        return candidates.singleOrNull()
    }
    private fun intent(command: AppCommand, description: String) = LocalNavigationIntent(listOf(command), description)
    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
