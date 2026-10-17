package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.model.activity.*
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.source.SourceAnchor
import dev.folio.model.workspace.Workspace
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecallStorageTest {
    @Test fun databaseReopenRetainsUnfinishedResponseAndEarlierReportedAttempts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "recall-${UUID.randomUUID()}.db")
        val source = SourceAnchor("doc", 0, "p")
        val recall = ActivityData.Recall("Prompt", "Reference", listOf(source), response = "Private unfinished response",
            pastAttempts = listOf(RecallAttempt("Earlier response", RecallRating.FORGOT, 1234L, true, true)))
        val state = Workspace(activities = mapOf("recall" to Activity("recall", source, recall)))
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { RoomWorkspaceRepository(first.workspace(), legacy).save(state) } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { assertEquals(state, RoomWorkspaceRepository(second.workspace(), legacy).load()) } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
