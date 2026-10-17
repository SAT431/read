package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceCorrectionStorageTest {
    @Test fun approvedCorrectionRecoversSeparatelyFromUnchangedMachineCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "correction-${UUID.randomUUID()}.db")
        val checksum = "a".repeat(64)
        val machine = SourcePage("source", 0, "Machine source", listOf(
            SourceElement("p0", SourceElementKind.PARAGRAPH, "Machine wording.", 0, SourceBounds(0f, 0f, 100f, 50f))),
            widthPoints = 100f, heightPoints = 100f)
        val correction = SourceCorrection("c1", SourceAnchor("source", 0, "p0"), checksum, "Machine wording.", "Approved wording.",
            author = CorrectionAuthor.AI, status = CorrectionStatus.APPROVED)
        val workspace = Workspace(sourceCorrections = mapOf(correction.id to correction), correctionDrafts = mapOf(
            "unfinished" to SourceCorrectionDraft(correction.source, checksum, correction.originalText, "Half edited wording.", correction.id)))
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        val key = PageCacheKey(checksum, "source", 0, 1)
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                RoomPageRepository(first.pages()).save(key, machine)
                RoomWorkspaceRepository(first.workspace(), legacy).save(workspace)
            } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                assertEquals(workspace, RoomWorkspaceRepository(second.workspace(), legacy).load())
                val recovered = RoomPageRepository(second.pages()).apply { load() }
                assertEquals(machine, recovered.find(key))
                assertTrue(requireNotNull(recovered.find(key)).corrections.isEmpty())
                assertEquals(EngineeringStatus.UNCHECKED, requireNotNull(recovered.find(key)).status)
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
