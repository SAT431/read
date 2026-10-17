package dev.folio.runtime.assistant

import dev.folio.model.assistant.*
import dev.folio.model.port.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Durable conversation/acknowledgement owner; never owns learner workspace mutation or provider keys. */
internal class AssistantConversation(private val history: AssistantHistoryRepository?) {
    val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    val results = mutableListOf<JsonObject>()
    private var restored = false
    private var historyAvailable = true
    suspend fun restore() {
        if (restored) return
        try { history?.load()?.let { AssistantHistoryRecovery.recover(it) }?.let {
            mutableState.value = it.state; results.addAll(it.executionResults.takeLast(8))
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            historyAvailable = false
            mutableState.value = state.value.copy(historyError = "Saved conversation could not be recovered. The original is preserved; new messages will last for this session only.")
        }
        restored = true
    }
    suspend fun checkpoint(): Boolean {
        if (!historyAvailable) return true
        try {
            val savedState = state.value.copy(historyError = null)
            history?.save(AssistantHistory(state = savedState, executionResults = results.toList()))
            mutableState.value = savedState; return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableState.value = state.value.copy(historyError = "Conversation could not be saved. New messages may be lost when the app closes.")
            return false
        }
    }
    fun append(role: MessageRole, text: String) {
        mutableState.value = state.value.copy(messages = (state.value.messages +
            AssistantMessage(UUID.randomUUID().toString(), role, text.take(12000))).takeLast(40))
    }
    fun fail(message: String, text: String) {
        val previous = state.value.acceptedActions.isNotEmpty()
        mutableState.value = state.value.copy(status = AssistantStatus.FAILED, activeRequestId = null,
            error = (if (previous) "Earlier app actions were applied and retained. " else "") +
                (if (previous) message.replace("Your command was not applied", "The latest step was not applied") else message) +
                (if (previous && !state.value.retrySafe) " Review current work before giving a new request." else ""),
            retryUtterance = text.takeIf { state.value.retrySafe })
    }
}
