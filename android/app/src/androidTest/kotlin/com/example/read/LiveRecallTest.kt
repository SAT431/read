package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.*
import dev.folio.catalog.Neet2026Catalog
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.remote.DirectGeminiMentor
import dev.folio.remote.ProviderTransport
import dev.folio.remote.RetryingProviderTransport
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real provider check using isolated labelled source/state and privately saved credentials. */
class LiveRecallTest {
    @Test fun geminiCreatesValidSourceLinkedRecallWithoutInventingLearnerHistory() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val catalog = ArchitectureSources.catalog()
        val curriculum = Neet2026Catalog()
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<LibraryPdf>())
            override suspend fun load() {}
        }
        val registry = BuiltInComponents.registry()
        val executor = CommandExecutor(registry, catalog)
        val initial = ArchitectureActivities.initial()
        val workspace = WorkspaceSession(initial, executor, object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(executor, curriculum, library, catalog))
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        var diagnostic = "not_started"
        val transport = ProviderTransport { request ->
            RetryingProviderTransport().exchange(request).also { reply ->
                diagnostic = "http_${reply.status}"
                if (reply.status == 200) {
                    val candidate = runCatching { Json.parseToJsonElement(reply.body).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
                    val finish = candidate?.get("finishReason")?.jsonPrimitive?.content
                    diagnostic += ":" + when (finish) { "STOP", "MAX_TOKENS", "SAFETY", "RECITATION" -> finish; else -> "other_finish" }
                }
            }
        }
        val mentor = DirectGeminiMentor(profile, prompt, transport)
        val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum, sourceCatalog = catalog), library, catalog,
            provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse = try {
                mentor.respond(request).also { diagnostic += ":decoded" }
            } catch (error: Exception) {
                diagnostic += ":${error.javaClass.simpleName}:" + error.stackTrace.take(3).joinToString { "${it.fileName}:${it.lineNumber}" }
                // Only structural names/paths from this isolated sample; never raw JSON or error text.
                val message = error.message.orEmpty()
                Regex("unknown key '([A-Za-z_]{1,50})'").find(message)?.groupValues?.get(1)?.let { diagnostic += ":field=$it" }
                Regex("""at path: ([${'$'}A-Za-z0-9_.\[\]]{1,160})""").find(message)?.groupValues?.get(1)?.let { diagnostic += ":path=$it" }
                throw error
            } } }, effects = { assertTrue(it.isEmpty()) })
        assistant.ask("Create one active-recall card (RECALL) about rough versus smooth endoplasmic reticulum using only this labelled sample page. " +
            "Use an existing passage source, a faithful reference answer, and show the card in the workspace. Do not enter my response, reveal it, rate it or invent history.")
        assertEquals("Live recall failed ($diagnostic): ${assistant.state.value.error}", AssistantStatus.COMPLETED, assistant.state.value.status)
        val activity = workspace.state.value.activities.values.single { it.data is ActivityData.Recall }
        val recall = activity.data as ActivityData.Recall
        ActivityValidator.validate(recall, activity.source, catalog)
        assertTrue(recall.response.isEmpty() && !recall.revealed && recall.rating == null && recall.pastAttempts.isEmpty())
        assertTrue(recall.sources.all(catalog::contains))
        assertTrue(workspace.state.value.components.values.any { it.activityId == activity.id && it.visible })
        assertTrue(workspace.traces.value.all { it.accepted })
        assertEquals(initial.activities.getValue("reader"), workspace.state.value.activities.getValue("reader"))
    }
}
