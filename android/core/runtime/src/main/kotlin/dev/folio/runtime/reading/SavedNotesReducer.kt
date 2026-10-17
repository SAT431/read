package dev.folio.runtime.reading

import dev.folio.model.activity.ActivityData
import dev.folio.model.command.*
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import dev.folio.model.workspace.*

/** Stored targets can be managed without loading, rewriting or claiming access to their original page. */
class SavedNotesReducer(private val catalog: SourceCatalog) {
    fun apply(state: Workspace, command: AppCommand, actor: CommandActor): Workspace {
        val activityId = when (command) {
            is AppCommand.OpenSavedAnnotation -> command.activityId
            is AppCommand.BeginSavedNoteEdit -> command.activityId
            is AppCommand.EditSavedDraft -> command.activityId
            is AppCommand.CommitSavedDraft -> command.activityId
            is AppCommand.DiscardSavedDraft -> command.activityId
            else -> error("Unknown saved-note command")
        }
        val activity = state.activities[activityId] ?: error("Unknown reader")
        val reader = activity.data as? ActivityData.Reader ?: error("Saved notes require a reader")
        if (command is AppCommand.OpenSavedAnnotation || command is AppCommand.BeginSavedNoteEdit) {
            val id = if (command is AppCommand.OpenSavedAnnotation) command.id else (command as AppCommand.BeginSavedNoteEdit).id
            val expected = if (command is AppCommand.OpenSavedAnnotation) command.expectedFingerprint else (command as AppCommand.BeginSavedNoteEdit).expectedFingerprint
            val note = reader.annotations[id] ?: error("Saved annotation unavailable")
            require(SavedNotes.fingerprint(activityId, note) == expected) { "Saved annotation changed; refresh first" }
            if (command is AppCommand.OpenSavedAnnotation) {
                val index = SavedNotes.resolve(note.source, note.sentenceId, catalog)
                return SavedReadingNavigation(catalog).open(state, activityId, note.source, note.sentenceId, index, 0, actor)
            }
            require(actor == CommandActor.USER) { "Only the learner can edit private drafts" }
            require(note.kind == AnnotationKind.NOTE)
            val draftId = "edit-${note.id}"
            require(draftId !in reader.annotationDrafts && reader.annotationDrafts.size < 200) { "An edit draft already exists; open Drafts" }
            val draft = AnnotationDraft(note.source, note.sentenceId, note.text, note.id, note.text)
            return update(state, activityId, reader.copy(annotationDrafts = reader.annotationDrafts + (draftId to draft)))
                .copy(navigation = state.navigation.copy(destination = AppDestination.NOTES, notesFilter = NotesFilter.DRAFTS, notesQuery = ""))
        }
        require(actor == CommandActor.USER) { "Only the learner can change private drafts" }
        val id = when (command) {
            is AppCommand.EditSavedDraft -> command.id
            is AppCommand.CommitSavedDraft -> command.id
            is AppCommand.DiscardSavedDraft -> command.id
            else -> error("Unknown draft command")
        }
        val expected = when (command) {
            is AppCommand.EditSavedDraft -> command.expectedFingerprint
            is AppCommand.CommitSavedDraft -> command.expectedFingerprint
            is AppCommand.DiscardSavedDraft -> command.expectedFingerprint
            else -> error("Unknown draft command")
        }
        val draft = reader.annotationDrafts[id] ?: error("Draft unavailable")
        require(SavedNotes.fingerprint(activityId, id, draft) == expected) { "Draft changed; refresh first" }
        val next = when (command) {
            is AppCommand.EditSavedDraft -> {
                require(command.text.length <= 2000)
                reader.copy(annotationDrafts = reader.annotationDrafts + (id to draft.copy(text = command.text)))
            }
            is AppCommand.DiscardSavedDraft -> reader.copy(annotationDrafts = reader.annotationDrafts - id)
            is AppCommand.CommitSavedDraft -> {
                require(draft.text.isNotBlank() && draft.text.length <= 2000)
                require(!command.asSeparateNote || draft.annotationId != null)
                val createNew = draft.annotationId == null || command.asSeparateNote
                val noteId = if (!createNew) draft.annotationId!! else command.newAnnotationId.also {
                    require(it != null && it.matches(Regex("[A-Za-z0-9_-]{1,100}")) && it !in reader.annotations && reader.annotations.size < 200)
                }!!
                val note = if (!createNew) {
                    val old = reader.annotations[noteId] ?: error("Original note unavailable; draft retained")
                    require(old.kind == AnnotationKind.NOTE && old.source == draft.source && old.sentenceId == draft.sentenceId && old.text == draft.expectedText) {
                        "Original note changed; draft retained"
                    }
                    old.copy(text = draft.text)
                } else SourceAnnotation(noteId, draft.source, draft.sentenceId, AnnotationKind.NOTE, draft.text, AnnotationAuthor.USER)
                reader.copy(annotations = reader.annotations + (noteId to note), annotationDrafts = reader.annotationDrafts - id)
            }
            else -> error("Unknown draft command")
        }
        return update(state, activityId, next)
    }
    private fun update(state: Workspace, activityId: String, reader: ActivityData.Reader) = state.copy(
        activities = state.activities + (activityId to state.activities.getValue(activityId).copy(data = reader)))
}
