package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.AppCommand
import dev.folio.model.source.NotesFilter
import dev.folio.runtime.reading.SavedNotes
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SavedNotesStorageTest {
    @Test fun freshRoomSessionRetainsPrivateDraftEditsMissingTargetsAndSearchAndConfirmedDiscard() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val path = File(context.cacheDir, "notes-storage-${UUID.randomUUID()}.db")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var expected: dev.folio.model.workspace.Workspace? = null
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, path.absolutePath).build()
            try {
                val fixture = ColdSourceFixture(first); val original = fixture.sampleState(); fixture.repository.save(original)
                fixture.session.attachWriter(scope); fixture.session.restore()
                val draft = (original.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts.getValue("saved-draft")
                fixture.act(AppCommand.EditSavedDraft("reader", "saved-draft", SavedNotes.fingerprint("reader", "saved-draft", draft), "Labelled durable private edit"))
                fixture.act(AppCommand.SetNotesFilter(NotesFilter.DRAFTS)); fixture.act(AppCommand.SearchNotes("private"))
                fixture.session.flush(); expected = fixture.session.state.value
            } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, path.absolutePath).build()
            try {
                val fixture = ColdSourceFixture(second); fixture.session.attachWriter(scope); fixture.session.restore()
                assertNull(fixture.session.storageError.value); assertEquals(expected, fixture.session.state.value)
                val state = fixture.session.state.value; val entry = SavedNotes.entries(state, fixture.catalog).single { it.kind == dev.folio.model.source.SavedNoteKind.DRAFT }
                assertFalse(entry.sourceAvailable); assertEquals("Labelled durable private edit", entry.text)
                fixture.act(AppCommand.DiscardSavedDraft(entry.activityId, entry.id, entry.fingerprint)); fixture.session.flush()
                val after = fixture.session.state.value; val reader = after.activities.getValue("reader").data as ActivityData.Reader
                val before = state.activities.getValue("reader").data as ActivityData.Reader
                assertTrue(reader.annotationDrafts.isEmpty()); assertEquals(before.annotations, reader.annotations)
                assertEquals(before.bookmarks, reader.bookmarks); assertEquals(before.readingConfirmations, reader.readingConfirmations)
                assertEquals(after, fixture.repository.load())
            } finally { second.close() }
        } finally { scope.coroutineContext.job.cancelAndJoin(); context.deleteDatabase(path.absolutePath) }
    }
}
