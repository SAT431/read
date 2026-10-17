package dev.folio.model.source

import kotlinx.serialization.Serializable

@Serializable enum class NotesFilter { ALL, NOTES, MARKS, DRAFTS, UNAVAILABLE }
enum class SavedNoteKind { NOTE, HIGHLIGHT, UNDERLINE, DRAFT }
/** A projection over original reader records; never another copy of editable learning data. */
data class SavedNoteEntry(val activityId: String, val id: String, val kind: SavedNoteKind,
    val source: SourceAnchor, val sentenceId: String?, val text: String, val fingerprint: String,
    val sourceAvailable: Boolean, val author: AnnotationAuthor? = null)
@Serializable
data class SavedAnnotationReference(val activityId: String, val id: String, val fingerprint: String,
    val source: SourceAnchor, val kind: AnnotationKind, val sourceAvailable: Boolean)
