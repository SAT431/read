package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.*
import dev.folio.local.source.*
import dev.folio.model.port.PageCacheKey
import dev.folio.model.source.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated temporary SQLite database; never reads/writes learner PDFs or credentials. */
class SourceCacheRepairStorageTest {
    private val json = Json { encodeDefaults = true }
    private val checksum = "a".repeat(64)
    private val page = SourcePage("repair-fixture", 0, "Labelled fixture", listOf(SourceElement("p", SourceElementKind.PARAGRAPH, "Exact labelled fixture text", 0)))
    private val good = SourcePageRow(checksum, page.documentId, 0, 1, json.encodeToString(page), "{}")
    private val bad = good.copy(pageIndex = 1, payload = "damaged fixture")
    private val report = good.copy(pageIndex = 2, payload = json.encodeToString(page.copy(pageIndex = 2)), fidelityPayload = "[]")
    @Test fun realRoomRepairBacksUpExactRowsAndKeepsWorkspaceLibraryAndOriginalPdf() = runBlocking {
        fixture { context, directory, database ->
            val originals = listOf(good, bad, report); originals.forEach { database.pages().save(it) }
            val originalFile = File(directory, "fixture.pdf")
            val pdf = android.graphics.pdf.PdfDocument()
            try {
                val sheet = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 300, 1).create())
                sheet.canvas.drawText("Labelled source-cache repair fixture", 10f, 30f, android.graphics.Paint())
                pdf.finishPage(sheet); originalFile.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            val bytes = originalFile.readBytes()
            val sample = ColdSourceFixture(database); val study = sample.sampleState(); sample.repository.save(study)
            val workspace = database.workspace().read()
            val library = LibraryPdfRow("fixture", "fixture-chapter", checksum, 1L, "labelled original metadata")
            database.library().insert(library)
            val cache = RoomPageRepository(database.pages(), PrivateSourceCacheBackup(File(directory, "backups")))
            assertTrue(runCatching { cache.load() }.isFailure)
            val receipt = cache.repair(cache.previewRepair().fingerprint)
            val backup = File(directory, "backups/${receipt.backupId}")
            assertEquals(originals.toSet(), backup.readLines(Charsets.UTF_8).drop(1).map { json.decodeFromString<SourcePageRow>(it) }.toSet())
            assertEquals(setOf(good, report.copy(fidelityPayload = null)), database.pages().readAll().toSet())
            assertEquals(workspace, database.workspace().read()); assertEquals(study, sample.repository.load())
            assertEquals(listOf(library), database.library().readAll()); assertArrayEquals(bytes, originalFile.readBytes())
            val recovered = RoomPageRepository(database.pages()); recovered.load()
            assertEquals(page, recovered.page(page.documentId, 0)); assertNull(recovered.fidelity(page.documentId, 2))
            cache.save(PageCacheKey(checksum, page.documentId, 1, 1), page.copy(pageIndex = 1))
            assertEquals(3, database.pages().readAll().size)
        }
    }
    @Test fun sqliteAbortRollsBackEarlierDeletionAndKeepsVerifiedBackup() = runBlocking {
        fixture { _, directory, database ->
            val otherBad = bad.copy(pageIndex = 3); val originals = listOf(good, bad, otherBad)
            originals.forEach { database.pages().save(it) }
            withContext(Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL("CREATE TRIGGER repair_fixture_abort BEFORE DELETE ON source_pages WHEN OLD.pageIndex = 3 AND (SELECT COUNT(*) FROM source_pages WHERE pageIndex = 1) = 0 BEGIN SELECT RAISE(ABORT, 'fixture abort'); END")
            }
            val backups = File(directory, "backups")
            val cache = RoomPageRepository(database.pages(), PrivateSourceCacheBackup(backups))
            assertTrue(runCatching { cache.load() }.isFailure)
            assertTrue(runCatching { cache.repair(cache.previewRepair().fingerprint) }.isFailure)
            assertEquals(originals.toSet(), database.pages().readAll().toSet())
            assertEquals(1, backups.listFiles()!!.count { it.name.endsWith(".jsonl") })
            assertNull(cache.page(page.documentId, 0)); assertEquals(0L, cache.revision.value)
            assertTrue(runCatching { cache.save(PageCacheKey(checksum, page.documentId, 0, 1), page) }.isFailure)
        }
    }
    private suspend fun fixture(block: suspend (Context, File, FolioDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "cache-repair-${UUID.randomUUID()}").apply { mkdirs() }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, File(directory, "fixture.db").absolutePath).build()
        try { block(context, directory, database) } finally { database.close(); directory.deleteRecursively() }
    }
}
