package dev.folio.runtime

import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecoveredSourceEvidenceTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "https://example.com"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private suspend fun request(catalog: SourceCatalog, verify: (MentorRequest) -> Unit) {
        val stored = TestFixture.reader()
        val workspace = WorkspaceSession(Workspace(), CommandExecutor(TestFixture.registry, catalog), object : WorkspaceRepository {
            override suspend fun load() = stored
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(TestFixture.registry, catalog))
        workspace.restore()
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { object : MentorProvider {
                override suspend fun respond(request: MentorRequest): MentorResponse {
                    verify(request); return MentorResponse(requestId = request.requestId, message = "Labelled response", batch = null)
                }
            } }, effects = {})
        assistant.ask("What saved context is available?")
        assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
    }
    @Test fun absentDocumentIsUnavailableRatherThanFalselyLabelledAsFixture() = runBlocking {
        val catalog = object : SourceCatalog by TestFixture.catalog {
            override fun documents() = emptyList<SourceDocument>()
            override fun page(documentId: String, pageIndex: Int): SourcePage? = null
            override fun contains(anchor: SourceAnchor) = false
        }
        request(catalog) {
            assertTrue(it.capabilityContext.getValue("sourceAvailabilityKnown").jsonPrimitive.boolean)
            assertFalse(it.sourceContext.getValue("activeSourceAvailable").jsonPrimitive.boolean)
            assertFalse(it.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean)
            assertEquals("unavailable_saved_source", it.sourceContext.getValue("sourceEvidenceKind").jsonPrimitive.content)
            assertFalse(it.sourceContext.containsKey("page"))
        }
    }
    @Test fun wrongPageOwnershipCannotBecomeAssistantSourceEvidence() = runBlocking {
        val catalog = object : SourceCatalog by TestFixture.catalog {
            override fun page(documentId: String, pageIndex: Int) = requireNotNull(TestFixture.catalog.page(documentId, pageIndex)).copy(documentId = "other-document")
        }
        request(catalog) {
            assertFalse(it.sourceContext.getValue("activeSourceAvailable").jsonPrimitive.boolean)
            assertFalse(it.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean)
            assertFalse(it.sourceContext.containsKey("page") || it.sourceContext.containsKey("selectedSentence"))
        }
    }
    @Test fun omittedCapabilityCatalogueReportsUnknownAvailabilityRatherThanKnownMissingSources() {
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum).encode(TestFixture.reader(), emptyList())).jsonObject
        assertFalse(context.getValue("sourceAvailabilityKnown").jsonPrimitive.boolean)
        assertEquals(0, context.getValue("unavailableSourceCount").jsonPrimitive.int)
        assertTrue(context.getValue("unavailableSources").jsonArray.isEmpty())
    }
}
