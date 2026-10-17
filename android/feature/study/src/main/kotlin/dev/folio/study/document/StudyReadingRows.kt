package dev.folio.study.document

import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.EngineeringStatus
import dev.folio.model.study.DocumentTopic
import dev.folio.runtime.study.DocumentStudyRules
import dev.folio.study.reader.*

internal data class StudyReadingSection(val pdfId:String,val documentId:String,val pageIndex:Int,
    val title:String,val topics:List<DocumentTopic>) {
    val key get()="$documentId:$pageIndex"
}
internal sealed interface StudyReadingRow {
    val key:String
    val section:StudyReadingSection
    data class Source(override val section:StudyReadingSection,val status:EngineeringStatus?):StudyReadingRow {
        override val key="${section.key}:source"
    }
    data class Content(override val section:StudyReadingSection,val block:ReadingBlock):StudyReadingRow {
        override val key="${section.key}:content:${block.source.id}"
    }
    data class Notice(override val section:StudyReadingSection,val text:String):StudyReadingRow {
        override val key="${section.key}:notice"
    }
}
internal fun readingRows(sections:List<StudyReadingSection>,catalog:SourceCatalog):List<StudyReadingRow> = sections.flatMap{section ->
    val page=catalog.page(section.documentId,section.pageIndex)
    val source=StudyReadingRow.Source(section,page?.status)
    if(page==null) listOf(source,StudyReadingRow.Notice(section,"Preparing linked text and original diagrams…"))
    else {
        val selected=DocumentStudyRules.elements(page,section.topics).map{it.id}.toSet()
        val blocks=ReadingCompiler().compile(page).filter{it.source.id in selected}
        listOf(source) + blocks.map{StudyReadingRow.Content(section,it)} +
            if(blocks.isEmpty()) listOf(StudyReadingRow.Notice(section,"No readable content matched this region. Inspect its original page.")) else emptyList()
    }
}
