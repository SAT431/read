package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.model.activity.*
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnnotationDraftStorageTest {
    @Test fun databaseCloseAndReopenRetainsUnfinishedDraftWithoutCreatingANote() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "draft-${UUID.randomUUID()}.db")
        val source = SourceAnchor("draft-fixture", 0, "paragraph")
        val pending = AnnotationDraft(source, "sentence", "Unfinished local draft")
        val original = Workspace(activities = mapOf("reader" to Activity("reader", source,
            ActivityData.Reader(annotationDrafts = mapOf("new-paragraph" to pending)))))
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { RoomWorkspaceRepository(first.workspace(), legacy).save(original) } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val recovered = requireNotNull(RoomWorkspaceRepository(second.workspace(), legacy).load())
                assertEquals(original, recovered)
                assertTrue((recovered.activities.getValue("reader").data as ActivityData.Reader).annotations.isEmpty())
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
