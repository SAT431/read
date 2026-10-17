package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable
data class SourceDocument(val id: String, val title: String, val pageCount: Int, val checksum: String, val localPdfPath: String?)

@Serializable
data class Chapter(val id: String, val title: String, val documentId: String, val firstPage: Int, val lastPage: Int)

@Serializable
enum class EngineeringStatus { NOT_PROCESSED, PROCESSING, UNCHECKED, REVIEWED, FAILED }

@Serializable
data class SourcePage(
    val documentId: String,
    val pageIndex: Int,
    val title: String,
    val elements: List<SourceElement>,
    val status: EngineeringStatus = EngineeringStatus.UNCHECKED,
    val engineeringVersion: Int = 1,
    val printedLabel: String? = null,
    val corrections: Map<String, String> = emptyMap(),
    val isFixture: Boolean = false,
    val widthPoints: Float? = null,
    val heightPoints: Float? = null,
)
