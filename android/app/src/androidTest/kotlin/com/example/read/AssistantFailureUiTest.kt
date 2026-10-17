package com.example.read

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.*
import dev.folio.assistant.AssistantStatusBar
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.remote.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantFailureUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun deadlineStopsSpinnerAndRetryUsesFreshViewWithoutReplayingCancelledRequest() {
        val catalog = ArchitectureSources.catalog()
        val curriculum = Neet2026Catalog()
        val registry = BuiltInComponents.registry()
        val commands = CommandExecutor(registry, catalog)
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val workspace = WorkspaceSession(ArchitectureActivities.initial(), commands, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(commands, curriculum, library, catalog))
        val json = Json { classDiscriminator = "type"; encodeDefaults = true }
        var calls = 0; var cancelled = false
        val revisions = mutableListOf<Long>()
        val transport = ProviderTransport { wire ->
            calls++
            val envelope = Json.parseToJsonElement(wire.body.toString(Charsets.UTF_8)).jsonObject
            val text = envelope.getValue("contents").jsonArray.first().jsonObject.getValue("parts").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
            val request = json.decodeFromString<MentorRequest>(text)
            val revision = request.capabilityContext.getValue("revision").jsonPrimitive.long
            revisions.add(revision)
            if (calls == 1) try { awaitCancellation() } finally { cancelled = true }
            val response = MentorResponse(requestId = request.requestId, message = "Opened home", batch = AppCommandBatch(
                requestId = request.requestId, expectedRevision = revision, commands = listOf(AppCommand.Navigate(AppDestination.HOME))))
            ServiceReply(200, buildJsonObject { put("candidates", buildJsonArray { add(buildJsonObject {
                put("finishReason", "STOP"); put("content", buildJsonObject { put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", json.encodeToString(response)) })
                }) })
            }) }) }.toString())
        }
        val mentor = DirectGeminiMentor(ProviderProfile("fixture-key", "fixture-groq", "fixture-model"), "fixture prompt", transport, 1000)
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum), library, catalog, { mentor }, {})
        compose.setContent { FolioTheme(false) {
            val state by assistant.state.collectAsState()
            val scope = rememberCoroutineScope()
            Column {
                TextButton({ scope.launch { assistant.ask("Open home") } }) { Text("Ask fixture command") }
                TextButton({ scope.launch { workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(
                    AppCommand.Navigate(AppDestination.SETTINGS))), ExecutionPolicy(CommandActor.USER)) } }) { Text("Change view manually") }
                AssistantStatusBar(state, {}, { scope.launch { assistant.ask(requireNotNull(assistant.state.value.retryUtterance)) } })
            }
        } }
        compose.onNodeWithText("Ask fixture command").performClick()
        compose.waitUntil(10000) { assistant.state.value.status == AssistantStatus.FAILED }
        compose.onNodeWithText("Request timed out.", substring = true).assertExists()
        compose.onNodeWithText("Retry command").assertIsEnabled()
        compose.runOnIdle { assertTrue(cancelled); assertEquals(1, calls); assertEquals(0L, workspace.state.value.revision) }
        compose.onNodeWithText("Change view manually").performClick()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS, workspace.state.value.navigation.destination) }
        compose.onNodeWithText("Retry command").performClick()
        compose.waitUntil(10000) { assistant.state.value.status == AssistantStatus.COMPLETED }
        compose.onNodeWithText("Retry command").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, calls); assertEquals(listOf(0L, 1L), revisions)
            assertEquals(2L, workspace.state.value.revision)
            assertEquals(AppDestination.HOME, workspace.state.value.navigation.destination)
            assertFalse(assistant.state.value.busy)
        }
    }
}
