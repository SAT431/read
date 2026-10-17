package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.read.fixture.*
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.CommandActor
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ReadingBookmarkStorageTest {
    @Test fun roomReopenKeepsExactSentenceFingerprintProvenanceAndSavedList() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "reading-bookmarks-${UUID.randomUUID()}.db")
        val catalog = ArchitectureSources.catalog()
        val sentence = SentenceIndex().compile(requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))).first()
        val reducer = ReadingBookmarkReducer(catalog) { 100L }
        val saved = reducer.apply(ArchitectureActivities.initial(), LearningInteraction.AddReadingBookmark("reader", "bookmark", sentence.source, sentence.id), CommandActor.AI)
        val state = reducer.apply(saved, LearningInteraction.ShowReadingBookmarks("reader", true), CommandActor.USER)
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { RoomWorkspaceRepository(first.workspace(), legacy).save(state) } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val restored = requireNotNull(RoomWorkspaceRepository(second.workspace(), legacy).load())
                assertEquals(state, restored)
                val data = restored.activities.getValue("reader").data as ActivityData.Reader
                assertTrue(data.bookmarksVisible); assertTrue(data.bookmarks.getValue("bookmark").byAssistant)
                val opened = reducer.apply(restored, LearningInteraction.OpenReadingBookmark("reader", "bookmark", data.bookmarks.getValue("bookmark").createdAtRevision), CommandActor.USER)
                assertEquals(sentence.id, (opened.activities.getValue("reader").data as ActivityData.Reader).selectedSentence)
                assertEquals(sentence.source, opened.context.source)
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
