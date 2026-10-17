package dev.folio.model.assistant

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantStatus { IDLE, REQUESTING, EXECUTING, PREPARING, COMPLETED, WAITING, REJECTED, FAILED, CANCELLED }
@Serializable
enum class MessageRole { USER, ASSISTANT, SYSTEM }
@Serializable
data class AssistantMessage(val id: String, val role: MessageRole, val text: String)
@Serializable
data class AssistantState(val status: AssistantStatus = AssistantStatus.IDLE,
    val messages: List<AssistantMessage> = emptyList(), val activeRequestId: String? = null,
    val error: String? = null, val retryUtterance: String? = null, val historyError: String? = null,
    val round: Int = 0, val acceptedActions: List<String> = emptyList(), val retrySafe: Boolean = true, val acceptedWorkspaceRevision: Long? = null) {
    val busy get() = status == AssistantStatus.REQUESTING || status == AssistantStatus.EXECUTING || status == AssistantStatus.PREPARING
}
