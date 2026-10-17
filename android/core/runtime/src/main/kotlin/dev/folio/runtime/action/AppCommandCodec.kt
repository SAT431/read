package dev.folio.runtime.action

import dev.folio.model.command.AppCommandBatch
import kotlinx.serialization.json.Json

class AppCommandCodec {
    private val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = false; encodeDefaults = true }
    fun decode(text: String): AppCommandBatch {
        require(text.length <= 150_000)
        return json.decodeFromString<AppCommandBatch>(text).also { require(it.schemaVersion == 1 && it.commands.size in 1..24) }
    }
    fun encode(batch: AppCommandBatch) = json.encodeToString(batch)
}
