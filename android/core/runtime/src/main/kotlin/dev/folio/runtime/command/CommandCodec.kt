package dev.folio.runtime.command

import dev.folio.model.command.CommandBatch
import kotlinx.serialization.json.Json

class CommandCodec {
    private val json = Json { ignoreUnknownKeys = false; classDiscriminator = "type" }
    fun decode(payload: String): CommandBatch {
        require(payload.length <= 100_000) { "Command payload too large" }
        return json.decodeFromString<CommandBatch>(payload).also {
            require(it.schemaVersion == 1) { "Unsupported command schema" }
            require(it.commands.size in 1..24) { "Command count outside limits" }
        }
    }
    fun encode(batch: CommandBatch): String = json.encodeToString(batch)
}
