package com.example.read

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.read.engineering.LocalPageEngineer
import dev.folio.local.database.FolioDatabase
import dev.folio.local.pdf.*
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.remote.*
import dev.folio.runtime.engineering.EngineeringCoordinator
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativeDirectPdfTest {
    @Test fun selectedPageOriginalCropAndRoomCacheWorkEntirelyOnDevice() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "native-pdf-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val original = File(File(context.filesDir, "pdfs").apply { mkdirs() }, "source.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawRect(40f, 60f, 140f, 160f, Paint().apply { color = Color.RED })
            page.canvas.drawText("ATP is energy.", 30f, 220f, Paint().apply { color = Color.BLACK; textSize = 16f })
            pdf.finishPage(page); original.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val checksum = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { "%02x".format(it) }
        val document = SourceDocument("native-test-doc", "Original", 1, checksum, original.path)
        val catalog = object : SourceCatalog {
            override fun documents() = listOf(document)
            override fun chapters() = emptyList<Chapter>()
            override fun page(documentId: String, pageIndex: Int): SourcePage? = null
            override fun contains(anchor: SourceAnchor) = anchor.documentId == document.id && anchor.pageIndex == 0
        }
        val profiles = object : ProviderProfileStore {
            override suspend fun load() = ProviderProfile("test-gemini", "test-groq", "test-model")
            override suspend fun save(profile: ProviderProfile) = Unit
            override suspend fun clear() = Unit
        }
        val database = Room.inMemoryDatabaseBuilder(base, FolioDatabase::class.java).build()
        try {
            val pages = RoomPageRepository(database.pages())
            var providerCalls = 0
            val engineer = LocalPageEngineer(profiles, LocalPdfPageExtraction(context, catalog, NativePdfTools(context.filesDir, catalog)),
                "test prompt", pages::stageFidelity) { _, evidence ->
                providerCalls++
                assertTrue(File(evidence.pdfPath).isFile)
                android.os.ParcelFileDescriptor.open(File(evidence.pdfPath), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { selected -> assertEquals(1, selected.pageCount) }
                }
                assertTrue(evidence.text.contains("ATP"))
                assertEquals(300f, evidence.width); assertEquals(400f, evidence.height)
                GeminiPageDraft("Energy", listOf(
                    GeminiDraftElement(SourceElementKind.FIGURE, "", listOf(40f, 60f, 140f, 160f)),
                    GeminiDraftElement(SourceElementKind.PARAGRAPH, "ATP is energy.", listOf(20f, 190f, 240f, 230f))))
            }
            val coordinator = EngineeringCoordinator(engineer, pages)
            val request = EngineeringRequest("native-test", document.id, 0, checksum, 1)
            val result = coordinator.ensure(request)
            assertEquals(result, coordinator.ensure(request))
            assertEquals(1, providerCalls)
            assertEquals(EngineeringStatus.UNCHECKED, result.status)
            assertTrue(result.corrections.isEmpty())
            val crop = File(File(context.filesDir, "source-assets"), result.elements.first().mediaId!!)
            val bitmap = BitmapFactory.decodeFile(crop.path)
            try { assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
            finally { bitmap.recycle() }
            val recovered = RoomPageRepository(database.pages()).apply { load() }
            assertEquals(result, recovered.find(PageCacheKey(checksum, document.id, 0, 1)))
            assertTrue(File(context.cacheDir, "selected-pages").listFiles().orEmpty().isEmpty())
            assertEquals(checksum, MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { "%02x".format(it) })
        } finally { database.close(); root.deleteRecursively() }
    }
}
