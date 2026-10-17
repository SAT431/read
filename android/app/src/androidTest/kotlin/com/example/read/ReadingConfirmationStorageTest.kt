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
import dev.folio.model.source.ReadingProgress
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.runtime.reading.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ReadingConfirmationStorageTest {
    @Test fun reopenedRoomKeepsExplicitReportEvidenceAndProgressWithoutInferringFromScroll() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "reading-confirmation-${UUID.randomUUID()}.db")
        val catalog = ArchitectureSources.catalog(); val page = requireNotNull(catalog.page(ArchitectureSources.DOCUMENT_ID, 0))
        val source = SentenceIndex().compile(page).first().source
        val reducer = ReadingConfirmationReducer(catalog) { 100L }
        val confirmed = reducer.apply(ArchitectureActivities.initial(),
            LearningInteraction.ConfirmReading("reader", source, null, 0), CommandActor.AI)
        val saved = reducer.apply(confirmed, LearningInteraction.ShowReadingCheckpoints("reader", true), CommandActor.USER)
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { RoomWorkspaceRepository(first.workspace(), legacy).save(saved) } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val restored = requireNotNull(RoomWorkspaceRepository(second.workspace(), legacy).load()); assertEquals(saved, restored)
                val reader = restored.activities.getValue("reader").data as ActivityData.Reader
                ReadingConfirmations.validate(reader)
                assertEquals(1L, reader.readingRevision)
                assertTrue(reader.readingCheckpointsVisible)
                assertTrue(reader.readingConfirmations.values.single().byAssistant)
                assertEquals(100L, reader.readingConfirmations.values.single().reportedAtEpochMillis)
                assertEquals(ReadingProgress(2, 1, 0, 0, reader.readingConfirmations.keys.single()), ReadingConfirmations.progress(reader, page, catalog))
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
