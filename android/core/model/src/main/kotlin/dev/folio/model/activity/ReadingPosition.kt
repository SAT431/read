package dev.folio.model.activity

import kotlinx.serialization.Serializable

@Serializable
data class ReadingPosition(val index: Int = 0, val offset: Int = 0)
