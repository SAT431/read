package com.example.read.engineering

import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.remote.*
import dev.folio.runtime.engineering.PageModelValidator
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Native composition of deterministic extraction, Gemini transcription and original-pixel crops. */
class LocalPageEngineer(private val profiles: ProviderProfileStore, private val extraction: LocalPageExtraction,
    private val prompt: String, private val fidelity: (EngineeringRequest, JsonObject) -> Unit,
    private val reconstruct: suspend (ProviderProfile, PdfPageEvidence) -> GeminiPageDraft = { profile, evidence ->
        DirectGeminiPageReconstructor(profile, prompt).reconstruct(evidence)
    }) : PageEngineer {
    override suspend fun engineer(request: EngineeringRequest): SourcePage {
        val profile = profiles.load() ?: error("Configure Gemini in Assistant settings")
        val evidence = extraction.extract(request)
        try {
            val draft = reconstruct(profile, evidence)
            var elements = draft.elements.mapIndexed { index, element ->
                val identity = "${request.documentChecksum}:${request.pageIndex}:$index:${element.kind}:${element.bounds}:${element.text}"
                val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
                SourceElement("native-$hash", element.kind, element.text, index,
                    SourceBounds(element.bounds[0], element.bounds[1], element.bounds[2], element.bounds[3]))
            }
            val provisional = SourcePage(request.documentId, request.pageIndex, draft.title, elements,
                engineeringVersion = request.engineeringVersion, widthPoints = evidence.width, heightPoints = evidence.height)
            PageModelValidator().validate(request, provisional)
            elements = elements.map { element ->
                currentCoroutineContext().ensureActive()
                if (element.kind == SourceElementKind.FIGURE) {
                    val crop=dev.folio.runtime.engineering.FigureCropPolicy.context(requireNotNull(element.bounds),evidence.width,evidence.height)
                    element.copy(mediaId=extraction.crop(request.documentId,request.pageIndex,crop),mediaBounds=crop)
                }
                else element
            }
            val result = provisional.copy(elements = elements)
            PageModelValidator().validate(request, result)
            fidelity(request, dev.folio.runtime.engineering.FidelityComparison().compare(
                evidence.text, elements.joinToString("\n") { it.originalText }, draft.uncertainties))
            return result
        } finally { withContext(NonCancellable + Dispatchers.IO) { File(evidence.pdfPath).delete() } }
    }
}
