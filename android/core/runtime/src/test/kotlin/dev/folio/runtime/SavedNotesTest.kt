package dev.folio.runtime

import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.reading.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SavedNotesTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = emptyList<Subject>(); override fun chapters() = emptyList<StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>()); override suspend fun load() {}
    }
    private val missing = object : SourceCatalog by TestFixture.catalog {
        override fun contains(anchor: SourceAnchor) = false
        override fun page(documentId: String, pageIndex: Int): SourcePage? = null
    }
    private val note = SourceAnnotation("note", TestFixture.source, kind = AnnotationKind.NOTE, text = "Saved note", author = AnnotationAuthor.AI)
    private val draft = AnnotationDraft(TestFixture.source, text = "Private unfinished draft")
    private fun state(): Workspace {
        val original = TestFixture.reader(); val activity = original.activities.getValue("read")
        return original.copy(activities = original.activities + ("read" to activity.copy(data = ActivityData.Reader(
            annotations = mapOf(note.id to note), annotationDrafts = mapOf("draft" to draft)))), navigation = AppNavigation(AppDestination.NOTES))
    }
    private fun executor(catalog: SourceCatalog = TestFixture.catalog) = AppCommandExecutor(TestFixture.executor, curriculum, library, catalog)
    private fun execute(state: Workspace, vararg commands: AppCommand, catalog: SourceCatalog = TestFixture.catalog, actor: CommandActor = CommandActor.USER) =
        executor(catalog).execute(state, AppCommandBatch(requestId = "fixture", commands = commands.toList()), ExecutionPolicy(actor))
    private fun reader(state: Workspace) = state.activities.getValue("read").data as ActivityData.Reader
    private fun draftHash(state: Workspace) = SavedNotes.fingerprint("read", "draft", reader(state).annotationDrafts.getValue("draft"))
    @Test fun projectionIncludesSuspendedReadersAndMissingTargetsWithoutMutatingRecords() {
        val state = state().let { it.copy(activities = it.activities.mapValues { (_, activity) -> activity.copy(status = ActivityStatus.SUSPENDED) }) }
        val entries = SavedNotes.entries(state, missing)
        assertEquals(setOf(SavedNoteKind.NOTE, SavedNoteKind.DRAFT), entries.map { it.kind }.toSet())
        assertTrue(entries.none { it.sourceAvailable }); assertTrue(entries.all { SavedNotes.matches(it, NotesFilter.UNAVAILABLE, "") })
        assertEquals(1, entries.count { SavedNotes.matches(it, NotesFilter.DRAFTS, "unfinished") })
        assertEquals("Private unfinished draft", reader(state).annotationDrafts.getValue("draft").text)
    }
    @Test fun sharedFilterSearchAndAdditiveWireCommandsAreBoundedAndAtomic() {
        val filtered = execute(state(), AppCommand.SetNotesFilter(NotesFilter.DRAFTS), AppCommand.SearchNotes("private"))
        assertTrue(filtered.trace.error, filtered.trace.accepted); assertEquals(NotesFilter.DRAFTS, filtered.state.navigation.notesFilter)
        assertEquals("private", filtered.state.navigation.notesQuery)
        val bad = execute(state(), AppCommand.SetNotesFilter(NotesFilter.NOTES), AppCommand.SearchNotes("x".repeat(201)))
        assertFalse(bad.trace.accepted); assertEquals(state(), bad.state)
        val batch = AppCommandBatch(requestId = "codec", commands = listOf(AppCommand.OpenSavedAnnotation("read", note.id, SavedNotes.fingerprint("read", note))))
        val codec = AppCommandCodec(); assertEquals(batch, codec.decode(codec.encode(batch)))
    }
    @Test fun availableAnnotationResumeKeepsDataAndUsesSentenceAndPinGuards() {
        val state = state(); val command = AppCommand.OpenSavedAnnotation("read", note.id, SavedNotes.fingerprint("read", note))
        val result = execute(state, command, actor = CommandActor.AI)
        assertTrue(result.trace.error, result.trace.accepted); assertEquals(AppDestination.WORKSPACE, result.state.navigation.destination)
        assertEquals(reader(state).annotations, reader(result.state).annotations); assertEquals(reader(state).annotationDrafts, reader(result.state).annotationDrafts)
        assertEquals(note.source, result.state.context.source); assertTrue(reader(result.state).readingConfirmations.isEmpty())
        val pinned = state.copy(components = state.components.mapValues { (_, component) -> component.copy(pinned = true) })
        assertFalse(execute(pinned, command, actor = CommandActor.AI).trace.accepted)
        assertTrue(execute(pinned, command).trace.accepted)
    }
    @Test fun missingOrChangedSentenceCannotSilentlyRetargetSavedNote() {
        val state = state(); val command = AppCommand.OpenSavedAnnotation("read", note.id, SavedNotes.fingerprint("read", note))
        assertFalse(execute(state, command, catalog = missing).trace.accepted)
        val changed = note.copy(sentenceId = "old-sentence")
        val altered = state.copy(activities = state.activities + ("read" to state.activities.getValue("read").copy(data = reader(state).copy(annotations = mapOf(note.id to changed)))))
        assertFalse(execute(altered, AppCommand.OpenSavedAnnotation("read", changed.id, SavedNotes.fingerprint("read", changed))).trace.accepted)
    }
    @Test fun draftEditAndCommitRetainMissingOriginalSourceAndAllOtherRecords() {
        val state = state(); val edited = execute(state, AppCommand.EditSavedDraft("read", "draft", draftHash(state), "Kept private edit"), catalog = missing)
        assertTrue(edited.trace.error, edited.trace.accepted)
        val committed = execute(edited.state, AppCommand.CommitSavedDraft("read", "draft", draftHash(edited.state), "new-note"), catalog = missing)
        assertTrue(committed.trace.error, committed.trace.accepted)
        val reader = reader(committed.state); assertTrue(reader.annotationDrafts.isEmpty()); assertEquals(note, reader.annotations[note.id])
        assertEquals(draft.source, reader.annotations.getValue("new-note").source); assertEquals(AnnotationAuthor.USER, reader.annotations.getValue("new-note").author)
        assertEquals("Kept private edit", reader.annotations.getValue("new-note").text)
    }
    @Test fun draftMutationCommandsAreRejectedForAiEvenWithCorrectFingerprints() {
        val state = state(); val hash = draftHash(state)
        listOf(AppCommand.EditSavedDraft("read", "draft", hash, "AI rewrite"), AppCommand.CommitSavedDraft("read", "draft", hash, "ai-note"),
            AppCommand.DiscardSavedDraft("read", "draft", hash), AppCommand.BeginSavedNoteEdit("read", note.id, SavedNotes.fingerprint("read", note))).forEach {
            val result = execute(state, it, actor = CommandActor.AI); assertFalse(result.trace.accepted); assertEquals(state, result.state)
        }
    }
    @Test fun staleEditCommitAndDiscardCannotEraseNewerDraftOrPartiallyCommitBatch() {
        val state = state(); val hash = draftHash(state)
        val edited = execute(state, AppCommand.EditSavedDraft("read", "draft", hash, "Newer private text")).state
        listOf(AppCommand.DiscardSavedDraft("read", "draft", hash), AppCommand.CommitSavedDraft("read", "draft", hash, "new"),
            AppCommand.EditSavedDraft("read", "draft", hash, "stale")).forEach { assertFalse(execute(edited, it).trace.accepted) }
        val batch = execute(state, AppCommand.EditSavedDraft("read", "draft", hash, "Newer"), AppCommand.DiscardSavedDraft("read", "draft", hash))
        assertFalse(batch.trace.accepted); assertEquals(state, batch.state)
    }
    @Test fun editingExistingNoteCannotOverwriteExistingDraftOrChangedOriginal() {
        val state = state(); val command = AppCommand.BeginSavedNoteEdit("read", note.id, SavedNotes.fingerprint("read", note))
        val begun = execute(state, command, catalog = missing); assertTrue(begun.trace.error, begun.trace.accepted)
        assertFalse(execute(begun.state, command, catalog = missing).trace.accepted)
        val draftId = "edit-${note.id}"; val old = reader(begun.state).annotationDrafts.getValue(draftId)
        val changedNote = note.copy(text = "Changed externally")
        val changed = begun.state.copy(activities = begun.state.activities + ("read" to begun.state.activities.getValue("read").copy(
            data = reader(begun.state).copy(annotations = mapOf(note.id to changedNote)))))
        val result = execute(changed, AppCommand.CommitSavedDraft("read", draftId, SavedNotes.fingerprint("read", draftId, old)), catalog = missing)
        assertFalse(result.trace.accepted); assertEquals(changed, result.state)
    }
    @Test fun explicitDiscardRemovesOnlyOneDraftAndBlankCommitCannotBecomeNote() {
        val state = state(); val result = execute(state, AppCommand.DiscardSavedDraft("read", "draft", draftHash(state)), catalog = missing)
        assertTrue(result.trace.accepted); assertEquals(reader(state).annotations, reader(result.state).annotations); assertTrue(reader(result.state).annotationDrafts.isEmpty())
        val blank = execute(state, AppCommand.EditSavedDraft("read", "draft", draftHash(state), " ")).state
        assertFalse(execute(blank, AppCommand.CommitSavedDraft("read", "draft", draftHash(blank), "new")).trace.accepted)
    }
    @Test fun annotationEditsByAiAndAnyNoteDeletionCannotSilentlyDiscardPrivateDrafts() {
        val start = execute(state(), AppCommand.BeginSavedNoteEdit("read", note.id, SavedNotes.fingerprint("read", note))).state
        val aiUpdate = execute(start, AppCommand.UpdateAnnotation("read", note.id, note.text, note.text), actor = CommandActor.AI)
        assertTrue(aiUpdate.trace.accepted); assertEquals(reader(start).annotationDrafts, reader(aiUpdate.state).annotationDrafts)
        val aiCreate = execute(start, AppCommand.CreateAnnotation("read", "new-ai-note", draft.source, kind = AnnotationKind.NOTE, text = draft.text), actor = CommandActor.AI)
        assertTrue(aiCreate.trace.accepted); assertEquals(reader(start).annotationDrafts, reader(aiCreate.state).annotationDrafts)
        for (actor in listOf(CommandActor.AI, CommandActor.USER)) {
            val removed = execute(start, AppCommand.RemoveAnnotation("read", note.id, note.text), actor = actor)
            assertTrue(removed.trace.accepted); assertTrue(reader(removed.state).annotations.isEmpty())
            assertEquals(reader(start).annotationDrafts, reader(removed.state).annotationDrafts)
        }
    }
    @Test fun missingOriginalNoteEditCanExplicitlyBecomeSeparateNoteWithSameSource() {
        val start = execute(state(), AppCommand.BeginSavedNoteEdit("read", note.id, SavedNotes.fingerprint("read", note))).state
        val removed = execute(start, AppCommand.RemoveAnnotation("read", note.id, note.text)).state
        val id = "edit-${note.id}"; val draft = reader(removed).annotationDrafts.getValue(id)
        val result = execute(removed, AppCommand.CommitSavedDraft("read", id, SavedNotes.fingerprint("read", id, draft), "separate", true), catalog = missing)
        assertTrue(result.trace.error, result.trace.accepted)
        val saved = reader(result.state).annotations.getValue("separate")
        assertEquals(note.source, saved.source); assertEquals(note.text, saved.text); assertEquals(AnnotationAuthor.USER, saved.author)
        assertFalse(id in reader(result.state).annotationDrafts); assertTrue("draft" in reader(result.state).annotationDrafts)
    }
    @Test fun providerContextRedactsDraftTextIdsFingerprintsAndNativeQueryButRetainsSavedAnnotationReferences() {
        val state = state().copy(navigation = AppNavigation(AppDestination.NOTES, notesQuery = "private secret search"))
        val text = AppCapabilityContext(curriculum, sourceCatalog = missing).encode(state, emptyList())
        assertFalse(text.contains("Private unfinished draft")); assertFalse(text.contains("private secret search")); assertFalse(text.contains(draftHash(state)))
        val context = Json.parseToJsonElement(text).jsonObject; assertEquals(1, context.getValue("privateDraftCount").jsonPrimitive.int)
        val saved = context.getValue("savedAnnotations").jsonArray.single().jsonObject
        assertEquals(SavedNotes.fingerprint("read", note), saved.getValue("fingerprint").jsonPrimitive.content)
        assertFalse(saved.getValue("sourceAvailable").jsonPrimitive.boolean)
        val actions = context.getValue("actions").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }
        listOf("notes.edit_note", "notes.edit_draft", "notes.commit_draft", "notes.discard_draft").forEach { assertFalse(actions.getValue(it).getValue("enabled").jsonPrimitive.boolean) }
    }
}
