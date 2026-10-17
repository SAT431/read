package dev.folio.runtime.assistant

import dev.folio.model.assistant.AssistantMessage
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.AppCapabilityContext
import kotlinx.serialization.json.*

internal class AssistantRequestBuilder(private val context: AppCapabilityContext, private val catalog: SourceCatalog,
    private val engineeringAvailable: Boolean, private val liveContext: () -> JsonObject, private val preparationAwaitAvailable: Boolean = false) {
    private val json = Json { encodeDefaults = true; classDiscriminator = "type" }
    fun build(id: String, text: String, captured: Workspace, capturedPdfs: List<LibraryPdf>, messages: List<AssistantMessage>,
        results: List<JsonObject>, round: Int, acceptedActions: List<String>, resumed: Boolean): MentorRequest {
            val viewingPdf = AssistantSourceSelection.viewingPdf(captured)
            val source = AssistantSourceSelection.resolve(captured, capturedPdfs, catalog)
            val page = source?.let { anchor -> catalog.page(anchor.documentId, anchor.pageIndex)?.takeIf {
                it.documentId == anchor.documentId && it.pageIndex == anchor.pageIndex
            } }
            val sourceDocument = source?.let { anchor -> catalog.documents().firstOrNull { it.id == anchor.documentId } }
            val sourceContext = buildJsonObject {
                put("appContext", liveContext())
                put("continuation", buildJsonObject {
                    put("round", round); put("maxRounds", 3); put("priorAcceptedActions", JsonArray(acceptedActions.map(::JsonPrimitive)))
                    put("resumeAfterFailure", resumed)
                    put("instruction", "Prior accepted actions already happened. Use fresh native context to finish only the remaining user request. Never replay learner mutations or infer platform/job completion.")
                })
                if (page != null) {
                    put("page", json.encodeToJsonElement(page))
                    val sentences = dev.folio.runtime.reading.SentenceIndex().compile(page)
                    put("sentences", json.encodeToJsonElement(sentences))
                    val selectedId = if (viewingPdf) null else (captured.activities[captured.context.focusedActivityId]?.data as? dev.folio.model.activity.ActivityData.Reader)?.selectedSentence
                    sentences.firstOrNull { it.id == selectedId }?.let { put("selectedSentence", json.encodeToJsonElement(it)) }
                }
                put("sourcePageAvailable", page != null)
                put("activeSourceAvailable", source?.let { dev.folio.runtime.source.SavedSourceReferences.available(it, catalog) } == true)
                put("pageEngineeringAvailable", engineeringAvailable)
                put("pageEngineeringAwaitAvailable", preparationAwaitAvailable)
                source?.let { put("activeSource", json.encodeToJsonElement(it)) }
                source?.let { catalog.fidelity(it.documentId, it.pageIndex)?.let { report -> put("fidelity", report) } }
                if (source != null) put("sourceEvidenceKind", when {
                    sourceDocument == null -> "unavailable_saved_source"
                    page?.isFixture == true -> "labelled_explorer_fixture"
                    sourceDocument.localPdfPath != null -> "imported_pdf_reference"
                    else -> "cached_source_without_original_pdf"
                })
                put("conversation", JsonArray(messages.takeLast(8).map {
                    buildJsonObject { put("role", it.role.name); put("text", it.text) }
                }))
            }
            return MentorRequest(requestId = id, utterance = text,
                capabilityContext = json.parseToJsonElement(context.encode(captured, capturedPdfs)).jsonObject,
                sourceContext = sourceContext, executionResults = results.takeLast(8))
    }
}
