package dev.folio.runtime.assistant

import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.runtime.action.AppActionRegistry
import dev.folio.runtime.state.WorkspaceSession
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID

internal data class AssistantRoundResult(val continueRequested: Boolean = false, val revision: Long = -1,
    val libraryEntries: List<LibraryPdf> = emptyList(), val effectsToAwait: List<AppEffect> = emptyList(),
    val workspaceSnapshot: dev.folio.model.workspace.Workspace? = null)
/** Validates and durably acknowledges exactly one proposed batch against its captured context. */
internal class AssistantRoundRunner(private val workspace: WorkspaceSession, private val library: PdfLibrary,
    private val catalog: SourceCatalog, private val provider: suspend () -> MentorProvider,
    private val effects: suspend (List<AppEffect>) -> Unit, private val interactionLocked: () -> Boolean,
    private val localNavigation: LocalNavigationCommands?, private val builder: AssistantRequestBuilder,
    private val conversation: AssistantConversation, private val effectAwaiter: AssistantEffectAwaiter? = null) {
    suspend fun run(text: String, resumed: Boolean): AssistantRoundResult {
        val mutable = conversation.mutableState; val state = conversation.state
        val id = UUID.randomUUID().toString()
        mutable.value = state.value.copy(status = AssistantStatus.REQUESTING, activeRequestId = id)
        val captured = workspace.state.value; val pdfs = library.entries.value; val sourceRevision = catalog.revision
        val request = builder.build(id, text, captured, pdfs, state.value.messages, conversation.results,
            state.value.round, state.value.acceptedActions, resumed)
        val local = if (state.value.round == 1 && !resumed) localNavigation?.resolve(text, captured, pdfs) else null
        val response = if (local == null) provider().respond(request) else MentorResponse(requestId = id,
            message = local.description, batch = AppCommandBatch(requestId = id, expectedRevision = captured.revision, commands = local.commands))
        currentCoroutineContext().ensureActive()
        require(response.schemaVersion == 1 && response.requestId == id) { "Invalid response identity" }
        val batch = response.batch
        require(!response.continueAfterExecution || batch != null) { "Continuation requires an executed batch" }
        if (batch == null) {
            conversation.append(MessageRole.ASSISTANT, response.message)
            mutable.value = state.value.copy(status = AssistantStatus.COMPLETED, activeRequestId = null, retryUtterance = null)
            return AssistantRoundResult()
        }
        if (library.entries.value != pdfs || catalog.revision != sourceRevision) {
            conversation.append(MessageRole.SYSTEM, "Library changed while the assistant was responding. Ask again with the updated library.")
            mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null, error = "Stale library context")
            return AssistantRoundResult()
        }
        require(batch.expectedRevision == captured.revision && batch.requestId == id) { "Invalid response revision" }
        val available = request.capabilityContext.getValue("actions").jsonArray.map { it.jsonObject }
            .filter { it["enabled"]?.jsonPrimitive?.booleanOrNull != false }.map { it.getValue("id").jsonPrimitive.content }.toSet()
        require(batch.commands.all { AppActionRegistry.id(it) in available }) { "Unadvertised action" }
        mutable.value = state.value.copy(status = AssistantStatus.EXECUTING, retryUtterance = null)
        return withContext(NonCancellable) {
            if (!conversation.checkpoint()) {
                mutable.value = state.value.copy(status = AssistantStatus.FAILED, activeRequestId = null,
                    retryUtterance = text.takeIf { state.value.retrySafe }, error = "Your command was not applied because recovery could not be saved. Check device storage and retry.")
                return@withContext AssistantRoundResult()
            }
            val result = workspace.dispatchApp(batch, ExecutionPolicy(CommandActor.AI, interactionLocked()),
                contextCurrent = { !interactionLocked() && library.entries.value == pdfs && catalog.revision == sourceRevision })
            conversation.results.add(buildJsonObject {
                put("requestId", id); put("accepted", result.trace.accepted); put("revision", result.state.revision)
                put("error", result.trace.error?.let(::JsonPrimitive) ?: JsonNull); put("effectsRequested", result.effects.size)
                put("executionOrigin", if (local == null) "gemini" else "on_device")
                put("actionIds", JsonArray(batch.commands.map { JsonPrimitive(AppActionRegistry.id(it)) }))
            })
            if (conversation.results.size > 8) conversation.results.removeAt(0)
            val rejection = if (!result.trace.accepted && interactionLocked())
                "Your writing is protected. Finish the active edit or leave that view, then ask again" else result.trace.error
            if (!result.trace.accepted) {
                conversation.append(MessageRole.SYSTEM, "Actions not applied: $rejection. Ask again with the current context.")
                mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null, error = rejection)
                return@withContext AssistantRoundResult()
            }
            mutable.value = state.value.copy(acceptedWorkspaceRevision = result.state.revision, acceptedActions = (state.value.acceptedActions + batch.commands.map(AppActionRegistry::id)).takeLast(216),
                retrySafe = state.value.retrySafe && result.effects.isEmpty() && batch.commands.all(AssistantContinuationPolicy::retrySafe))
            if (local != null) conversation.append(MessageRole.SYSTEM, "On-device navigation: ${response.message}")
            else { conversation.append(MessageRole.ASSISTANT, response.message); conversation.append(MessageRole.SYSTEM, "App actions applied. Any Android picker still needs your selection.") }
            effects(result.effects)
            val prepare = response.continueAfterExecution && effectAwaiter?.supports(result.effects) == true
            val continueNow = response.continueAfterExecution && (result.effects.isEmpty() || prepare)
            if (response.continueAfterExecution && !continueNow) conversation.append(MessageRole.SYSTEM,
                "The requested platform action or background job needs to finish before another step. Continue with a new request afterward.")
            mutable.value = state.value.copy(status = if (prepare) AssistantStatus.PREPARING else if (continueNow) AssistantStatus.REQUESTING else if (response.continueAfterExecution) AssistantStatus.WAITING else AssistantStatus.COMPLETED,
                activeRequestId = null, error = null, retryUtterance = text.takeIf { continueNow && state.value.retrySafe })
            if (!conversation.checkpoint()) {
                mutable.value = state.value.copy(status = AssistantStatus.FAILED, activeRequestId = null, retryUtterance = null,
                    error = "Earlier app actions were applied, but their acknowledgement could not be saved. Check current work before a new request.")
                return@withContext AssistantRoundResult()
            }
            AssistantRoundResult(continueNow, result.state.revision, pdfs, result.effects.takeIf { prepare }.orEmpty(), result.state)
        }
    }
}
