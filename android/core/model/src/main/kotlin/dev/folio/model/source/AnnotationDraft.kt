package dev.folio.model.source

import kotlinx.serialization.Serializable

/** Local editor recovery; excluded from provider capability context. */
@Serializable
data class AnnotationDraft(val source: SourceAnchor, val sentenceId: String? = null, val text: String,
    val annotationId: String? = null, val expectedText: String? = null)
