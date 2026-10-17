package dev.folio.model.port

import dev.folio.model.source.SourcePage

data class EngineeringRequest(val jobId: String, val documentId: String, val pageIndex: Int,
    val documentChecksum: String, val engineeringVersion: Int)

/** Adapter must preserve original figures and corrections; it never returns executable UI code. */
interface PageEngineer { suspend fun engineer(request: EngineeringRequest): SourcePage }
