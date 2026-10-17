package dev.folio.runtime

import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.*
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LocalNavigationSessionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"; override val sourceUrl = "https://example.com"
        override fun subjects() = listOf(Subject(SubjectId.BOTANY, "Botany", "Plants"))
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell", 1, 1))
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(listOf(LibraryPdf("pdf", "cell", TestFixture.catalog.documents().first().copy(title = "Cells.pdf"), 0)))
        override suspend fun load() {}
    }
    private fun workspace(initial: Workspace = TestFixture.reader()) = WorkspaceSession(initial, TestFixture.executor,
        object : WorkspaceRepository { override suspend fun load(): Workspace? = null; override suspend fun save(workspace: Workspace) {} },
        WorkspaceValidator(TestFixture.registry, TestFixture.catalog), appExecutor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog))
    private fun assistant(workspace: WorkspaceSession, locked: Boolean = false, history: AssistantHistoryRepository? = null,
        respond: suspend (MentorRequest) -> MentorResponse) = AssistantSession(workspace, AppCapabilityContext(curriculum), library, TestFixture.catalog,
        { object : MentorProvider { override suspend fun respond(request: MentorRequest) = respond(request) } }, {},
        interactionLocked = { locked }, history = history, localNavigation = LocalNavigationCommands(curriculum))
    @Test fun exactNavigationSkipsProviderAndLaterMentorReceivesRealUpdatedContextAndAcknowledgements() = runBlocking {
        val workspace = workspace()
        var calls = 0
        val assistant = assistant(workspace) { request ->
            calls++
            assertEquals(1, request.sourceContext.getValue("activeSource").jsonObject.getValue("pageIndex").jsonPrimitive.int)
            assertEquals(3, request.executionResults.size)
            assertTrue(request.executionResults.all { it.getValue("executionOrigin").jsonPrimitive.content == "on_device" })
            MentorResponse(requestId = request.requestId, message = "Fixture tutor response", batch = null)
        }
        for (text in listOf("Open Botany", "Open Cells.pdf", "Go to the next page")) {
            assistant.ask(text); assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
        }
        assertEquals(0, calls); assertEquals(1, workspace.state.value.navigation.pdfPageIndex)
        assertEquals(3, assistant.state.value.messages.count { it.role == MessageRole.SYSTEM && it.text.startsWith("On-device navigation:") })
        assertFalse(assistant.state.value.messages.any { it.role == MessageRole.ASSISTANT })
        assistant.ask("Explain this page")
        assertEquals(1, calls); assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
    }
    @Test fun writingProtectionAndInvalidPageBoundariesStillRejectWithoutCallingProvider() = runBlocking {
        val workspace = workspace()
        val blocked = assistant(workspace, locked = true) { error("Provider must not be called") }
        blocked.ask("Open Botany")
        assertEquals(AssistantStatus.REJECTED, blocked.state.value.status); assertEquals(0L, workspace.state.value.revision)
        val ready = assistant(workspace) { error("Provider must not be called") }
        ready.ask("Open Cells.pdf")
        val before = workspace.state.value
        ready.ask("Open file page 99")
        assertEquals(AssistantStatus.REJECTED, ready.state.value.status); assertEquals(before, workspace.state.value)
    }
    @Test fun checkpointsStillPreventApplyingLocalActionsWhenDurableHistoryFails() = runBlocking {
        val workspace = workspace()
        val history = object : AssistantHistoryRepository {
            override suspend fun load(): AssistantHistory? = null
            override suspend fun save(history: AssistantHistory) { error("disk unavailable") }
        }
        val assistant = assistant(workspace, history = history) { error("Provider must not be called") }
        assistant.ask("Open Botany")
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
        assertEquals(0L, workspace.state.value.revision)
        assertEquals("Open Botany", assistant.state.value.retryUtterance)
    }
}
