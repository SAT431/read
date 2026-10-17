package dev.folio.model.activity

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable
data class Question(val id: String, val prompt: String, val options: List<String>, val correctOption: Int,
    val explanation: String, val sources: List<SourceAnchor>)
