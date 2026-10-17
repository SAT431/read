package dev.folio.runtime.assistant

import dev.folio.model.assistant.*
import dev.folio.model.command.AppEffect
import dev.folio.model.port.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.state.WorkspaceSession
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One user intent can use up to three fresh validated rounds; accepted batches are never rolled back. */
class AssistantSession(private val workspace: WorkspaceSession, context: AppCapabilityContext,
    private val library: PdfLibrary, catalog: SourceCatalog,
    provider: suspend () -> MentorProvider, effects: suspend (List<AppEffect>) -> Unit,
    private val interactionLocked: () -> Boolean = { false }, engineeringAvailable: Boolean = false,
    liveContext: () -> JsonObject = { JsonObject(emptyMap()) }, history: AssistantHistoryRepository? = null,
    localNavigation: LocalNavigationCommands? = null,
    private val readyForContinuation: suspend (Workspace) -> Unit = {}, private val turnTimeoutMillis: Long = 90_000,
    private val effectAwaiter: AssistantEffectAwaiter? = null) {
    private val requests = Mutex()
    private val conversation = AssistantConversation(history)
    private val runner = AssistantRoundRunner(workspace, library, catalog, provider, effects, interactionLocked, localNavigation,
        AssistantRequestBuilder(context, catalog, engineeringAvailable, liveContext, effectAwaiter != null), conversation, effectAwaiter)
    val state = conversation.state
    init { require(turnTimeoutMillis in 1..120_000) }
    suspend fun restore() = requests.withLock { conversation.restore() }
    suspend fun ask(text: String) = run(text, false)
    suspend fun retry() { restore(); state.value.retryUtterance?.takeIf { state.value.status == AssistantStatus.FAILED }?.let { run(it, true) } }
    private suspend fun run(text: String, resumed: Boolean) {
        require(text.isNotBlank() && text.length <= 4000)
        if (!requests.tryLock()) return
        val mutable = conversation.mutableState
        try {
            conversation.restore()
            if (resumed && state.value.acceptedActions.isNotEmpty() && state.value.acceptedWorkspaceRevision != workspace.state.value.revision) {
                mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null, retryUtterance = null,
                    error = "Your study view changed after the earlier step. Give a new request from the current view.")
                return
            }
            if (!resumed) conversation.append(MessageRole.USER, text)
            else conversation.append(MessageRole.SYSTEM, "Continuing the interrupted request using fresh context. Earlier applied actions are retained.")
            mutable.value = state.value.copy(status = AssistantStatus.REQUESTING, activeRequestId = null, error = null,
                retryUtterance = text, round = 0, acceptedActions = if (resumed) state.value.acceptedActions else emptyList(),
                retrySafe = if (resumed) state.value.retrySafe else true, acceptedWorkspaceRevision = if (resumed) state.value.acceptedWorkspaceRevision else null)
            conversation.checkpoint()
            withTimeout(turnTimeoutMillis) {
                for (round in 1..3) {
                    val expected = workspace.state.value; val expectedPdfs = library.entries.value
                    mutable.value = state.value.copy(round = round)
                    readyForContinuation(expected)
                    currentCoroutineContext().ensureActive()
                    if (workspace.state.value.revision != expected.revision || library.entries.value != expectedPdfs) {
                        mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null,
                            error = "Study context changed. Earlier app actions are retained; ask again from your current view.", retryUtterance = null)
                        return@withTimeout
                    }
                    val outcome = runner.run(text, resumed)
                    if (!outcome.continueRequested) return@withTimeout
                    if (round == 3) { conversation.fail("This request reached its three-step limit. Review the current view before continuing.", text); return@withTimeout }
                    if (outcome.effectsToAwait.isNotEmpty()) {
                        val prepared = AssistantPreparationWait.await(requireNotNull(effectAwaiter), outcome.effectsToAwait,
                            requireNotNull(outcome.workspaceSnapshot), workspace.state, library, outcome.libraryEntries)
                        conversation.results.add(buildJsonObject {
                            put("kind", "native_source_jobs"); put("completed", true)
                            put("pages", JsonArray(prepared.map { page -> buildJsonObject {
                                put("jobId", page.jobId); put("documentId", page.source.documentId); put("pageIndex", page.source.pageIndex)
                                put("engineeringVersion", page.engineeringVersion); put("sourcePageAvailable", page.sourcePageAvailable); put("kind",page.kind)
                            } }))
                        })
                        if (conversation.results.size > 8) conversation.results.removeAt(0)
                        conversation.append(MessageRole.SYSTEM, "Native source jobs finished. Topic maps and reconstructions remain unchecked; inspect the completion kinds and fresh context before the next step.")
                        mutable.value = state.value.copy(status = AssistantStatus.REQUESTING)
                        if (!conversation.checkpoint()) {
                            mutable.value = state.value.copy(status = AssistantStatus.FAILED, activeRequestId = null, retryUtterance = null,
                                error = "Page data was cached, but recovery could not be saved. Check current work before a new request.")
                            return@withTimeout
                        }
                    } else if (workspace.state.value.revision != outcome.revision || library.entries.value != outcome.libraryEntries) {
                        mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null,
                            error = "Study context changed after an applied step. Continue from the current view.", retryUtterance = null)
                        return@withTimeout
                    }
                }
            }
        } catch (_: PreparationContextChanged) {
            mutable.value = state.value.copy(status = AssistantStatus.REJECTED, activeRequestId = null, retryUtterance = null,
                error = "Study context changed during page preparation. No follow-up actions were applied; the page job may finish caching.")
        } catch (error: dev.folio.runtime.engineering.PagePreparationException) { conversation.fail(error.message.orEmpty(), text) }
        catch (_: TimeoutCancellationException) {
            val background = if (state.value.status == AssistantStatus.PREPARING) " Requested page work may still finish caching." else ""
            conversation.fail("The request reached its time limit. Try again from current context." + background, text)
        }
        catch (cancelled: CancellationException) {
            if (state.value.busy) mutable.value = state.value.copy(status = AssistantStatus.CANCELLED, activeRequestId = null,
                error = if (state.value.status == AssistantStatus.PREPARING) "Assistant stopped. Already-requested page processing may continue in the background." else state.value.error)
            throw cancelled
        } catch (error: ProviderApiException) { conversation.fail(error.message.orEmpty(), text) }
        catch (error: ProviderFailureException) { conversation.fail(error.message.orEmpty(), text) }
        catch (_: Exception) { conversation.fail("Assistant request failed. Check the Gemini API key, model, connection and quota.", text) }
        finally { try { withContext(NonCancellable) { conversation.checkpoint() } } finally { requests.unlock() } }
    }
}
