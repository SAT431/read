package dev.folio.model.port

import dev.folio.model.assistant.AssistantState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AssistantHistory(val schemaVersion: Int = 1, val state: AssistantState = AssistantState(),
    val executionResults: List<JsonObject> = emptyList())

interface AssistantHistoryRepository {
    suspend fun load(): AssistantHistory?
    suspend fun save(history: AssistantHistory)
}
