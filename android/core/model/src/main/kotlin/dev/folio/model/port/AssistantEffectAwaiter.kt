package dev.folio.model.port

import dev.folio.model.command.AppEffect
import dev.folio.model.source.SourceAnchor

/** Native completion metadata only, never a model's assertion that a background effect succeeded. */
data class PreparedSourceResult(val jobId: String, val source: SourceAnchor, val engineeringVersion: Int,
    val kind: String = "page_preparation", val sourcePageAvailable: Boolean = true)
interface AssistantEffectAwaiter {
    fun supports(effects: List<AppEffect>): Boolean
    suspend fun await(effects: List<AppEffect>): List<PreparedSourceResult>
}
