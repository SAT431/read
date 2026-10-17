package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.workspace.*
import dev.folio.runtime.assistant.LocalNavigationCommands
import org.junit.Assert.*
import org.junit.Test

class LocalNavigationTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"), Subject(SubjectId.ZOOLOGY, "Zoology", "Animals"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell structure", 1, 1),
            StudyChapter("motion", SubjectId.PHYSICS, "Laws of Motion", 1, 2))
    }
    private val pdf = LibraryPdf("pdf", "cell", SourceDocument("doc", "Cells.pdf", 4, "hash", null), 0)
    private val resolver = LocalNavigationCommands(curriculum)
    private val original = Workspace(navigation = AppNavigation(AppDestination.ASSISTANT, pdfId = "pdf", assistantOrigin = AppDestination.PDF))
    @Test fun exactSubjectsChaptersAndNamedDocumentsUseExistingIdsAndNormalizeSpeechPunctuation() {
        assertEquals(listOf(AppCommand.OpenSubject(SubjectId.BOTANY)), resolver.resolve("Please open   BOTANY.", Workspace(), emptyList())?.commands)
        assertEquals(listOf(AppCommand.OpenChapterLibrary("cell")), resolver.resolve("Could you please open Cell structure?", Workspace(), emptyList())?.commands)
        assertEquals(listOf(AppCommand.OpenPdf("pdf")), resolver.resolve("open cells", Workspace(), listOf(pdf))?.commands)
        assertEquals(listOf(AppCommand.OpenPdf("pdf")), resolver.resolve("open cells", Workspace(), listOf(pdf.copy(document = pdf.document.copy(title = "Cells.Pdf"))))?.commands)
    }
    @Test fun knownNewtonChapterAliasResolvesOnlyExplicitNavigationAndPreservesAmbiguity() {
        for (title in listOf("newtons laws of motion", "Newton's laws of motion", "Newton’s laws of motion", "Laws of Motion")) {
            for (suffix in listOf("", " chapter")) assertEquals(title + suffix,
                listOf(AppCommand.OpenChapterLibrary("motion")), resolver.resolve("open " + title + suffix, Workspace(), emptyList())?.commands)
        }
        for (text in listOf("explain Newton's laws of motion", "open Newton's laws of motion and Botany", "don't open Newton's laws of motion", "open Newton's law"))
            assertNull(text, resolver.resolve(text, Workspace(), emptyList()))
        val ambiguous = pdf.copy(document = pdf.document.copy(title = "Newtons laws of motion.pdf"))
        assertNull(resolver.resolve("open newtons laws of motion", Workspace(), listOf(ambiguous)))
    }
    @Test fun compoundNegatedUnknownAndEducationalLanguageRemainForGemini() {
        for (text in listOf("What is Botany?", "Don't open Botany", "Open Botany and Zoology", "Open biology", "Open that chapter", "Explain next page", "page 4"))
            assertNull(text, resolver.resolve(text, original, listOf(pdf)))
    }
    @Test fun pageCommandsResolveUsingCurrentOriginalOrWorkspaceContext() {
        assertEquals(listOf(AppCommand.NextPdfPage), resolver.resolve("go to the next page", original, listOf(pdf))?.commands)
        assertEquals(listOf(AppCommand.NextPdfPage), resolver.resolve("I want to go next page", original, listOf(pdf))?.commands)
        assertEquals(listOf(AppCommand.OpenPdf("pdf")), resolver.resolve("Once open that PDF, please.", original, listOf(pdf))?.commands)
        assertEquals(listOf(AppCommand.PreviousPdfPage), resolver.resolve("previous page", original, listOf(pdf))?.commands)
        assertEquals(listOf(AppCommand.PdfPage(2)), resolver.resolve("open file page 3", original, listOf(pdf))?.commands)
        val reader = TestFixture.reader().copy(navigation = AppNavigation(AppDestination.ASSISTANT, assistantOrigin = AppDestination.WORKSPACE))
        assertEquals(listOf(AppCommand.WorkspaceAction(WorkspaceCommand.NextPage), AppCommand.Navigate(AppDestination.WORKSPACE)),
            resolver.resolve("next page", reader, listOf(pdf))?.commands)
        assertNull(resolver.resolve("next page", Workspace(), listOf(pdf)))
    }
    @Test fun deicticPdfUsesActualCurrentDocumentAndDoesNotGuessAcrossMultipleChapterPdfs() {
        assertEquals(listOf(AppCommand.OpenPdf("pdf")), resolver.resolve("open that pdf", original, listOf(pdf))?.commands)
        val reader = TestFixture.reader().copy(navigation = AppNavigation(AppDestination.WORKSPACE, pdfId = "other"))
        assertEquals(listOf(AppCommand.StudyPdf("pdf")), resolver.resolve("study this pdf", reader, listOf(pdf))?.commands)
        assertNull(resolver.resolve("open that pdf", Workspace(navigation = AppNavigation(AppDestination.CHAPTER, chapterId = "cell")),
            listOf(pdf, pdf.copy(id = "other", document = pdf.document.copy(id = "other")))))
    }
    @Test fun duplicateTitlesAndCrossEntityAmbiguityAreDelegatedInsteadOfSelectingFirst() {
        val duplicate = pdf.copy(id = "other", chapterId = "other-chapter", document = pdf.document.copy(id = "other"))
        assertNull(resolver.resolve("open cells.pdf", Workspace(), listOf(pdf, duplicate)))
        val scoped = Workspace(navigation = AppNavigation(AppDestination.CHAPTER, chapterId = "cell"))
        assertEquals(listOf(AppCommand.OpenPdf("pdf")), resolver.resolve("open cells.pdf", scoped, listOf(pdf, duplicate))?.commands)
        assertNull(resolver.resolve("open botany", Workspace(), listOf(pdf.copy(document = pdf.document.copy(title = "Botany.pdf")))))
    }
}
