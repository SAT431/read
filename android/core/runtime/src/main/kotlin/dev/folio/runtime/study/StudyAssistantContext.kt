package dev.folio.runtime.study

import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import kotlinx.serialization.json.*

/** Bounded source discovery and exact native links. Never includes paths, keys or private drafts. */
object StudyAssistantContext {
    fun build(state: Workspace, library: PdfLibrary, outlines: DocumentOutlineRepository): JsonObject = buildJsonObject {
        val pdf = library.entries.value.firstOrNull { it.id == state.navigation.pdfId }
        put("documents",JsonArray(library.entries.value.sortedBy { if (it.chapterId == pdf?.chapterId) 0 else 1 }.take(100).map { source ->
            buildJsonObject {
                put("pdfId",source.id); put("title",source.document.title); put("chapterId",source.chapterId); put("pageCount",source.document.pageCount)
                val map = outlines.get(source.document.id)?.takeIf { it.checksum == source.document.checksum }
                put("mappingComplete",map?.indexedPages?.size == source.document.pageCount)
                map?.let { put("outlineFingerprint",DocumentStudyRules.fingerprint(it)); put("topicCount",it.topics.size) }
            }
        }))
        put("documentsTruncated",library.entries.value.size > 100)
        val browsing = library.entries.value.firstOrNull { it.id == state.navigation.harnessPdfId }
        browsing?.let { source -> outlines.get(source.document.id)?.takeIf { it.checksum == source.document.checksum }?.let { map ->
            val found = map.topics.filter { state.navigation.harnessTopicQuery.isBlank() ||
                (it.title + " " + it.subtopics.joinToString(" ")).contains(state.navigation.harnessTopicQuery,ignoreCase=true) }
            put("browse",buildJsonObject {
                put("pdfId",source.id); put("outlineFingerprint",DocumentStudyRules.fingerprint(map)); put("page",state.navigation.harnessTopicPage)
                put("total",found.size); put("mappingComplete",map.indexedPages.size == source.document.pageCount)
                put("topics",Json.encodeToJsonElement(found.drop(state.navigation.harnessTopicPage * 40).take(40)))
            })
        } }
        if (pdf == null) return@buildJsonObject
        put("pdfId",pdf.id); put("documentId",pdf.document.id)
        val outline = outlines.get(pdf.document.id)?.takeIf { it.checksum == pdf.document.checksum }
        put("mappingComplete",outline?.indexedPages?.size == pdf.document.pageCount)
        outline?.let {
            put("outlineFingerprint",DocumentStudyRules.fingerprint(it)); put("indexedPageCount",it.indexedPages.size)
            put("topicCount",it.topics.size); put("topicsTruncated",it.topics.size > 80)
            put("topics",Json.encodeToJsonElement(it.topics.take(80).map { topic -> topic.copy(summary=topic.summary.take(300)) }))
        }
        state.studyPlans[pdf.id]?.let { plan ->
            put("plan",Json.encodeToJsonElement(plan))
            if(plan.mode==dev.folio.model.study.StudyMode.READING) runCatching{
                StudyComposition.surface(plan,StudySources.resolve(plan,library.entries.value,outlines))
            }.getOrNull()?.let{surface -> put("compositionSurface",buildJsonObject{
                put("pdfId",surface.root.source.pdf.id);put("pageIndex",surface.root.pageIndex)
                put("sectionCount",surface.sections.size);put("sectionsTruncated",surface.sections.size>100)
                put("sections",JsonArray(surface.sections.take(100).map{ref->buildJsonObject{
                    put("pdfId",ref.source.pdf.id);put("documentId",ref.source.pdf.document.id);put("pageIndex",ref.pageIndex)
                }}))
            })}
        }
        put("guidance","Use harness.browse_topics for missing or truncated topic links. Include topics adds source references to this study collection, never edits original PDF bytes. Only explicit learner confirmation permits study.finished_line. Topic maps and reconstructed text are unchecked interpretations.")
    }
}
