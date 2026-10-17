package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.study.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.study.DocumentStudyRules
import kotlinx.coroutines.flow.MutableStateFlow

internal class DocumentStudyFixture {
    val pdf = LibraryPdf("pdf", "chapter", SourceDocument("doc", "Controlled", 4, "hash", "/private/doc.pdf"), 0)
    val library = object : PdfLibrary { override val entries = MutableStateFlow(listOf(pdf)); override suspend fun load() = Unit }
    val outlines = object : DocumentOutlineRepository {
        override val outlines = MutableStateFlow(mapOf("doc" to DocumentOutline("doc", "hash", 4, listOf(0,1,2,3),
            listOf(DocumentTopic("a", "First topic", "Source", 0, 0), DocumentTopic("b", "Second topic", "Source", 1, 2)))))
        override suspend fun load(document: SourceDocument) = Unit
        override suspend fun save(outline: DocumentOutline) { outlines.value = outlines.value + (outline.documentId to outline) }
    }
    val catalog = object : SourceCatalog by TestFixture.catalog {
        override fun documents() = listOf(pdf.document)
        override fun chapters() = listOf(Chapter("library-pdf", "Controlled", "doc", 0, 3))
        override fun page(documentId: String, pageIndex: Int) = TestFixture.catalog.page(documentId,pageIndex)?.copy(engineeringVersion=PageEngineeringVersion.CURRENT)
    }
    val commands = dev.folio.runtime.command.CommandExecutor(TestFixture.registry, catalog)
    val actual = AppCommandExecutor(commands, curriculum(), library, catalog, studyOutlines = outlines)
    var state = Workspace()
    fun curriculum() = object : CurriculumCatalog {
        override val baseline = "Fixture"; override val sourceUrl = "fixture"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    fun run(command: AppCommand, actor: CommandActor = CommandActor.USER) = actual.execute(state,
        AppCommandBatch(requestId = "test", commands = listOf(command)), ExecutionPolicy(actor)).also { if (it.trace.accepted) state = it.state }
    fun open(resume:Boolean=false) { check(run(AppCommand.StudyPdf("pdf",resume)).trace.accepted) }
    fun select(vararg ids: String) { check(run(AppCommand.SelectStudyTopics("pdf", DocumentStudyRules.fingerprint(outlines.get("doc")!!), ids.toList())).trace.accepted) }
    fun begin() { check(run(AppCommand.BeginStudy("pdf", plan.version)).trace.accepted) }
    val plan get() = state.studyPlans.getValue("pdf")
}
