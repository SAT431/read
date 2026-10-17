package dev.folio.runtime

import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AssistantSessionTest {
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
    private fun workspace(initial: Workspace = TestFixture.reader()) = WorkspaceSession(initial, TestFixture.executor,
        object : WorkspaceRepository { override suspend fun load(): Workspace? = null; override suspend fun save(workspace: Workspace) {} },
        WorkspaceValidator(TestFixture.registry, TestFixture.catalog),
        appExecutor = AppCommandExecutor(TestFixture.executor, curriculum, library))
    private fun assistant(workspace: WorkspaceSession, respond: suspend (MentorRequest) -> MentorResponse,
        locked: Boolean = false) = AssistantSession(workspace, AppCapabilityContext(curriculum), library, TestFixture.catalog,
        provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest) = respond(request) } },
        effects = {}, interactionLocked = { locked })
    private fun response(request: MentorRequest) = MentorResponse(requestId = request.requestId, message = "Opening home",
        batch = AppCommandBatch(requestId = request.requestId,
            expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long,
            commands = listOf(AppCommand.Navigate(AppDestination.HOME))))

    @Test fun acceptedActionsAndAcknowledgementsUseRealRuntime() = runBlocking {
        val workspace = workspace()
        var requestNumber = 0
        val assistant = assistant(workspace, {
            if (requestNumber++ == 1) assertTrue(it.executionResults.single().getValue("accepted").jsonPrimitive.boolean)
            response(it)
        })
        assistant.ask("Open home")
        assertEquals(1L, workspace.state.value.revision)
        assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
        assistant.ask("Open home again")
        assertEquals(2L, workspace.state.value.revision)
    }
    @Test fun replyCannotMoveAnAppChangedDuringNetworkWait() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, {
            workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(AppCommand.Navigate(AppDestination.SETTINGS))), TestFixture.user)
            response(it)
        })
        assistant.ask("Open home")
        assertEquals(AppDestination.SETTINGS, workspace.state.value.navigation.destination)
        assertEquals(AssistantStatus.REJECTED, assistant.state.value.status)
        assertFalse(assistant.state.value.messages.any { it.role == MessageRole.ASSISTANT })
    }
    @Test fun cancellationPreventsDelayedActions() = runBlocking {
        val workspace = workspace()
        val ready = CompletableDeferred<Unit>()
        val assistant = assistant(workspace, { ready.complete(Unit); awaitCancellation() })
        val job = launch { assistant.ask("Open home") }
        ready.await(); job.cancelAndJoin()
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.CANCELLED, assistant.state.value.status)
    }
    @Test fun protectedInteractionRejectsActions() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, { response(it) }, locked = true)
        assistant.ask("Open home")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.REJECTED, assistant.state.value.status)
    }
    @Test fun wrongRequestIdentityDoesNotChangeWorkspace() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, { response(it).copy(requestId = "wrong") })
        assistant.ask("Open home")
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
        assertEquals(0L, workspace.state.value.revision)
    }
    @Test fun enteringAssistantPreservesPdfOrigin() {
        val reducer = LibraryNavigationReducer(curriculum)
        val before = Workspace(navigation = AppNavigation(destination = AppDestination.PDF, pdfPageIndex = 3))
        val after = requireNotNull(reducer.reduce(before, AppCommand.Navigate(AppDestination.ASSISTANT), emptyList()))
        assertEquals(AppDestination.PDF, after.navigation.assistantOrigin)
    }
    @Test fun originalPdfPageOverridesOlderReaderSourceInAssistantRequest() = runBlocking {
        library.entries.value = listOf(dev.folio.model.library.LibraryPdf("pdf", "chapter",
            TestFixture.catalog.documents().first(), 0))
        val workspace = workspace(TestFixture.reader().copy(navigation = AppNavigation(AppDestination.ASSISTANT,
            pdfId = "pdf", pdfPageIndex = 3, assistantOrigin = AppDestination.PDF)))
        val assistant = assistant(workspace, {
            assertEquals(3, it.sourceContext.getValue("activeSource").jsonObject.getValue("pageIndex").jsonPrimitive.int)
            assertEquals("labelled_explorer_fixture", it.sourceContext.getValue("sourceEvidenceKind").jsonPrimitive.content)
            MentorResponse(requestId = it.requestId, message = "Page 4 context captured", batch = null)
        })
        assistant.ask("What page am I viewing?")
        assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
    }
    @Test fun providerFailureDoesNotExposeSecretsOrChangeWorkspace() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, { error("private-provider-secret") })
        assistant.ask("Open home")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
        assertFalse(assistant.state.value.error!!.contains("private-provider-secret"))
    }
    @Test fun classifiedProviderFailuresReachRecoveryWithoutChangingWorkspaceOrLosingCommand() = runBlocking {
        for (reason in ProviderFailureReason.entries) {
            val workspace = workspace()
            val expected = ProviderFailureException(AiProvider.GEMINI, reason)
            val assistant = assistant(workspace, { throw expected })
            assistant.ask("Open home")
            assertEquals(0L, workspace.state.value.revision)
            assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
            assertEquals(expected.message, assistant.state.value.error)
            assertEquals("Open home", assistant.state.value.retryUtterance)
            assertFalse(assistant.state.value.busy)
        }
    }
    @Test fun libraryImportDuringRequestRejectsOldToolPlan() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, {
            library.entries.value = listOf(dev.folio.model.library.LibraryPdf("new-pdf", "chapter",
                TestFixture.catalog.documents().first(), 0))
            response(it)
        })
        assistant.ask("Open home")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.REJECTED, assistant.state.value.status)
    }
    @Test fun directProviderCannotUseAnUnadvertisedCapability() = runBlocking {
        val workspace = workspace()
        val assistant = assistant(workspace, { request ->
            response(request).copy(batch = response(request).batch!!.copy(commands = listOf(
                AppCommand.FocusSentence("read", "not-advertised"))))
        })
        assistant.ask("Focus this sentence")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
    }
    @Test fun quotaMetadataReachesUserWithoutChangingWorkspaceOrRetrying() = runBlocking {
        val workspace = workspace()
        var calls = 0
        val assistant = assistant(workspace, { calls++; throw ProviderApiException(AiProvider.GEMINI, 429, 120) })
        assistant.ask("Go to next page")
        assertEquals(1, calls)
        assertEquals(0L, workspace.state.value.revision)
        assertEquals(AssistantStatus.FAILED, assistant.state.value.status)
        assertTrue(assistant.state.value.error!!.contains("Quota"))
        assertTrue(assistant.state.value.error!!.contains("120"))
    }
    @Test fun failedCommandCanBeRetriedWithFreshContextAndExecutesOnlyOnce() = runBlocking {
        val workspace = workspace()
        var calls = 0
        val ids = mutableSetOf<String>()
        val assistant = assistant(workspace, {
            assertTrue(ids.add(it.requestId))
            if (++calls == 1) throw ProviderApiException(AiProvider.GEMINI, 503)
            response(it)
        })
        assistant.ask("Open home")
        assertEquals(0L, workspace.state.value.revision)
        assertEquals("Open home", assistant.state.value.retryUtterance)
        workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(
            AppCommand.Navigate(AppDestination.SETTINGS))), TestFixture.user)
        assistant.ask(requireNotNull(assistant.state.value.retryUtterance))
        assertEquals(2, calls)
        assertEquals(2L, workspace.state.value.revision)
        assertEquals(AppDestination.HOME, workspace.state.value.navigation.destination)
        assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
        assertNull(assistant.state.value.retryUtterance)
    }
}
