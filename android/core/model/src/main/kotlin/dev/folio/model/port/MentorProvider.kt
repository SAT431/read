package dev.folio.model.port

import dev.folio.model.command.AppCommandBatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MentorRequest(val schemaVersion: Int = 1, val requestId: String, val utterance: String,
    val capabilityContext: JsonObject, val sourceContext: JsonObject = JsonObject(emptyMap()),
    val executionResults: List<JsonObject> = emptyList())
@Serializable
data class MentorResponse(val schemaVersion: Int = 1, val requestId: String,
    val message: String, val batch: AppCommandBatch?, val continueAfterExecution: Boolean = false)
interface MentorProvider { suspend fun respond(request: MentorRequest): MentorResponse }
