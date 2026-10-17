package dev.folio.model.workspace

import dev.folio.model.source.SourceAnchor
import kotlinx.serialization.Serializable

@Serializable
data class StudyContext(val chapterId: String? = null, val source: SourceAnchor? = null,
    val focusedActivityId: String? = null, val originalPdfVisible: Boolean = false,
    val recentSources: List<SourceAnchor> = emptyList(),
    val chapterCheckpoints: Map<String, SourceAnchor> = emptyMap())

@Serializable
enum class InteractionLock { NONE, TEXT_SELECTION, WRITING, ANSWERING, DRAGGING }
