package dev.folio.runtime.reading

import dev.folio.model.activity.ActivityData
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

object SavedNotes {
    private val json = Json { encodeDefaults = true }
    fun fingerprint(activityId: String, annotation: SourceAnnotation) = hash(activityId, "annotation", json.encodeToString(annotation))
    fun fingerprint(activityId: String, id: String, draft: AnnotationDraft) = hash(activityId, "draft:$id", json.encodeToString(draft))
    private fun hash(activity: String, kind: String, payload: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${activity.length}:$activity:${kind.length}:$kind:$payload".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun resolve(source: SourceAnchor, sentenceId: String?, catalog: SourceCatalog): Int {
        require(dev.folio.runtime.source.SavedSourceReferences.available(source, catalog)) { "Saved source is unavailable" }
        val page = catalog.page(source.documentId, source.pageIndex) ?: error("Saved page unavailable")
        val element = page.elements.firstOrNull { it.id == source.elementId && it.kind in setOf(
            SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST) } ?: error("Saved passage unavailable")
        sentenceId?.let { id -> require(SentenceIndex().compileElement(page, element.id).any { it.id == id }) { "Saved sentence changed" } }
        return page.elements.sortedBy { it.order }.indexOfFirst { it.id == element.id }.also { require(it >= 0) }
    }
    fun entries(state: Workspace, catalog: SourceCatalog?): List<SavedNoteEntry> = state.activities.toSortedMap().flatMap { (activityId, activity) ->
        val reader = activity.data as? ActivityData.Reader ?: return@flatMap emptyList()
        fun available(source: SourceAnchor, sentence: String?) = catalog != null && runCatching { resolve(source, sentence, catalog) }.isSuccess
        reader.annotations.toSortedMap().values.map { note -> SavedNoteEntry(activityId, note.id, SavedNoteKind.valueOf(note.kind.name),
            note.source, note.sentenceId, note.text, fingerprint(activityId, note), available(note.source, note.sentenceId), note.author) } +
            reader.annotationDrafts.toSortedMap().map { (id, draft) -> SavedNoteEntry(activityId, id, SavedNoteKind.DRAFT,
                draft.source, draft.sentenceId, draft.text, fingerprint(activityId, id, draft), available(draft.source, draft.sentenceId)) }
    }
    fun matches(entry: SavedNoteEntry, filter: NotesFilter, query: String): Boolean = when (filter) {
        NotesFilter.ALL -> true
        NotesFilter.NOTES -> entry.kind == SavedNoteKind.NOTE
        NotesFilter.MARKS -> entry.kind in setOf(SavedNoteKind.HIGHLIGHT, SavedNoteKind.UNDERLINE)
        NotesFilter.DRAFTS -> entry.kind == SavedNoteKind.DRAFT
        NotesFilter.UNAVAILABLE -> !entry.sourceAvailable
    } && (query.isBlank() || listOf(entry.text, entry.source.documentId, entry.source.elementId.orEmpty())
        .any { it.lowercase(Locale.ROOT).contains(query.trim().lowercase(Locale.ROOT)) })
}
