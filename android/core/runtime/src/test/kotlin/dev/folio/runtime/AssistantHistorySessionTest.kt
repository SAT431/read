package dev.folio.runtime

import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AssistantHistorySessionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "test"
        override val sourceUrl = "https://test.example/syllabus"
        override fun subjects() = emptyList<dev.folio.model.library.Subject>()
        override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
        override suspend fun load() {}
    }
    private class MemoryHistory : AssistantHistoryRepository {
        var saved: AssistantHistory? = null
        var failRead = false
        var failCommitCheckpoint = false
        var beforeCommit: (() -> Unit)? = null
        override suspend fun load(): AssistantHistory? { check(!failRead); return saved }
        override suspend fun save(history: AssistantHistory) {
            if (history.state.status == AssistantStatus.EXECUTING) beforeCommit?.invoke()
            check(!failCommitCheckpoint || history.state.status != AssistantStatus.EXECUTING)
            saved = history
        }
    }
    private val workspace = WorkspaceSession(TestFixture.reader(), TestFixture.executor,
        object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(TestFixture.registry, TestFixture.catalog),
        appExecutor = AppCommandExecutor(TestFixture.executor, curriculum, library))
    private fun session(history: AssistantHistoryRepository, reply: suspend (MentorRequest) -> MentorResponse) =
        AssistantSession(workspace, AppCapabilityContext(curriculum), library, TestFixture.catalog,
            provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest) = reply(request) } },
            effects = {}, history = history)
    private fun action(request: MentorRequest) = MentorResponse(requestId = request.requestId, message = "Home opened",
        batch = AppCommandBatch(requestId = request.requestId,
            expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long,
            commands = listOf(AppCommand.Navigate(AppDestination.HOME))))

    @Test fun recreatedSessionRestoresConversationAndExecutionAcknowledgementsWithoutReplaying() = runBlocking {
        val history = MemoryHistory()
        session(history, ::action).ask("Open home")
        assertEquals(1L, workspace.state.value.revision)
        val restored = session(history) {
            assertEquals(1, it.executionResults.size)
            assertTrue(it.sourceContext.getValue("conversation").toString().contains("Home opened"))
            MentorResponse(requestId = it.requestId, message = "Ready", batch = null)
        }
        restored.restore()
        assertEquals(3, restored.state.value.messages.size)
        assertEquals(1L, workspace.state.value.revision)
        restored.ask("Continue")
        assertEquals(AssistantStatus.COMPLETED, restored.state.value.status)
        assertEquals(1L, workspace.state.value.revision)
    }
    @Test fun failedCommitCheckpointPreventsAppActions() = runBlocking {
        val history = MemoryHistory().apply { failCommitCheckpoint = true }
        val assistant = session(history, ::action)
        assistant.ask("Open home")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
        assertEquals("Open home", assistant.state.value.retryUtterance)
        assertTrue(assistant.state.value.error!!.contains("not applied"))
    }
    @Test fun unreadableHistoryIsNotOverwrittenAndAssistantRemainsUsable() = runBlocking {
        val original = AssistantHistory(state = AssistantState(messages = listOf(
            AssistantMessage("old", MessageRole.USER, "Preserve this"))))
        val history = MemoryHistory().apply { saved = original; failRead = true }
        val assistant = session(history) { MentorResponse(requestId = it.requestId, message = "Ready", batch = null) }
        assistant.ask("Can you help?")
        assertEquals(original, history.saved)
        assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
        assertNotNull(assistant.state.value.historyError)
    }
    @Test fun writingStartingDuringRecoveryCheckpointRejectsActionWithoutReplay() = runBlocking {
        val gate = dev.folio.runtime.interaction.InteractionGate()
        val history = MemoryHistory().apply { beforeCommit = { gate.set("editor", true) } }
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum), library, TestFixture.catalog,
            provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest) = action(request) } },
            effects = { assertFalse("Protected action emitted effects", gate.locked); assertTrue(it.isEmpty()) },
            history = history, interactionLocked = { gate.locked })
        assistant.ask("Open home")
        assertEquals(AssistantStatus.REJECTED, assistant.state.value.status)
        assertEquals(0L, workspace.state.value.revision)
        gate.set("editor", false)
        assertEquals(0L, workspace.state.value.revision)
        history.beforeCommit = null
        assistant.ask("Open home now")
        assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
        assertEquals(1L, workspace.state.value.revision)
    }
}
