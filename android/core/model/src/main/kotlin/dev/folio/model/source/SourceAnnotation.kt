package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable enum class AnnotationKind { NOTE, HIGHLIGHT, UNDERLINE }
@Serializable enum class AnnotationAuthor { USER, AI }

/** Learner-owned activity data; never a correction to the original transcription. */
@Serializable
data class SourceAnnotation(val id: String, val source: SourceAnchor, val sentenceId: String? = null,
    val kind: AnnotationKind, val text: String = "", val author: AnnotationAuthor = AnnotationAuthor.USER)
