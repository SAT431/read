package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.study.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.study.DocumentStudyRules
import kotlinx.coroutines.flow.MutableStateFlow

internal class PdfHarnessFixture {
    val first = LibraryPdf("first","chapter",SourceDocument("first-doc","First.pdf",3,"first-hash","/private/first.pdf"),0)
    val second = LibraryPdf("second","chapter",SourceDocument("second-doc","Second.pdf",3,"second-hash","/private/second.pdf"),1)
    val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(first,second)); override suspend fun load() = Unit
    }
    val maps = MutableStateFlow(listOf(first,second).associate { pdf -> pdf.document.id to
            DocumentOutline(pdf.document.id,pdf.document.checksum,3,listOf(0,1,2),listOf(
                DocumentTopic("${pdf.id}-a","${pdf.id} alpha","Source description",0,0),
                DocumentTopic("${pdf.id}-b","${pdf.id} beta","Source description",1,1,hasFigures=true),
                DocumentTopic("${pdf.id}-c","${pdf.id} gamma","Source description",2,2))) })
    val outlines = object : DocumentOutlineRepository {
        override val outlines = maps
        override suspend fun load(document: SourceDocument) = Unit
        override suspend fun save(outline: DocumentOutline) { outlines.value = outlines.value + (outline.documentId to outline) }
    }
    val available = mutableSetOf("first-doc","second-doc")
    val catalog = object : SourceCatalog {
        override fun contains(anchor: SourceAnchor) = documents().any { it.id==anchor.documentId && anchor.pageIndex in 0 until it.pageCount } &&
            (anchor.elementId==null || page(anchor.documentId,anchor.pageIndex)?.elements?.any { it.id==anchor.elementId }==true)
        override fun documents() = listOf(first.document,second.document)
        override fun chapters() = listOf(first,second).map { Chapter("library-${it.id}",it.document.title,it.document.id,0,2) }
        override fun page(documentId: String,pageIndex: Int): SourcePage? = if(documentId !in available || pageIndex !in 0..2) null else
            SourcePage(documentId,pageIndex,"Controlled",listOf(SourceElement("p",SourceElementKind.PARAGRAPH,
                "$documentId page $pageIndex sentence.",0,SourceBounds(10f,10f,300f,50f)),
                SourceElement("figure",SourceElementKind.FIGURE,"Original diagram",1,SourceBounds(10f,100f,300f,300f),mediaId="fixture.png")),
                engineeringVersion=PageEngineeringVersion.CURRENT,widthPoints=600f,heightPoints=800f)
    }
    val curriculum = object : CurriculumCatalog {
        override val baseline="Fixture";override val sourceUrl="fixture"
        override fun subjects()=emptyList<dev.folio.model.library.Subject>()
        override fun chapters()=listOf(dev.folio.model.library.StudyChapter("chapter",dev.folio.model.library.SubjectId.BOTANY,"Cell",11,1))
    }
    val executor = AppCommandExecutor(dev.folio.runtime.command.CommandExecutor(TestFixture.registry,catalog),curriculum,library,catalog,studyOutlines=outlines)
    var state = Workspace()
    val plan get()=state.studyPlans.getValue(first.id)
    fun fingerprint(pdf: LibraryPdf)=DocumentStudyRules.fingerprint(outlines.get(pdf.document.id)!!)
    fun run(command: AppCommand,actor: CommandActor=CommandActor.USER)=executor.execute(state,AppCommandBatch(requestId="harness",commands=listOf(command)),ExecutionPolicy(actor))
        .also { if(it.trace.accepted) state=it.state }
    fun start() {
        check(run(AppCommand.HarnessOpenTopic(first.id,fingerprint(first),"first-a")).trace.accepted)
    }
    fun include(vararg topicIds: String)=run(AppCommand.HarnessIncludeTopics(first.id,second.id,plan.version,fingerprint(second),topicIds.toList()))
}
