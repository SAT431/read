package com.example.read

import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.engineering.LocalPageEngineer
import dev.folio.local.database.FolioDatabase
import dev.folio.local.pdf.*
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.runtime.engineering.EngineeringCoordinator
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in integration test: only a generated, known PDF page is sent to Gemini. */
class LivePdfEngineeringTest {
    @Test fun selectedPageLiveReconstructionOriginalFigureAndOfflineRecovery() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val base = ApplicationProvider.getApplicationContext<Context>()
        val profiles = KeystoreProviderProfileStore(base)
        assertNotNull("Save Gemini API key first", profiles.load())
        val root = File(base.cacheDir, "live-pdf-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val original = File(File(context.filesDir, "pdfs").apply { mkdirs() }, "controlled.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                val paint = Paint().apply { color = Color.BLACK; textSize = 20f; isAntiAlias = true }
                page.canvas.drawColor(Color.WHITE)
                if (index == 1) {
                    page.canvas.drawText("Cell Biology", 40f, 70f, paint)
                    page.canvas.drawText("ATP stores chemical energy.", 40f, 120f, paint)
                    page.canvas.drawText("DNA carries genetic information.", 40f, 160f, paint)
                    page.canvas.drawRect(80f, 240f, 320f, 440f, Paint().apply { color = Color.RED })
                    page.canvas.drawText("Figure 1. Original red rectangle.", 40f, 480f, paint)
                } else page.canvas.drawText("CONTROL_PAGE_$index", 40f, 70f, paint)
                pdf.finishPage(page)
            }
            original.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val checksum = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { "%02x".format(it) }
        val document = SourceDocument("live-controlled-doc", "Controlled PDF", 3, checksum, original.path)
        val catalog = object : SourceCatalog {
            override fun documents() = listOf(document)
            override fun chapters() = emptyList<Chapter>()
            override fun page(documentId: String, pageIndex: Int): SourcePage? = null
            override fun contains(anchor: SourceAnchor) = anchor.documentId == document.id && anchor.pageIndex in 0..2
        }
        val database = Room.inMemoryDatabaseBuilder(base, FolioDatabase::class.java).build()
        try {
            val pages = RoomPageRepository(database.pages())
            val extraction = LocalPdfPageExtraction(context, catalog, NativePdfTools(context.filesDir, catalog))
            val checkedExtraction = object : LocalPageExtraction {
                override suspend fun extract(request: EngineeringRequest): PdfPageEvidence = extraction.extract(request).also {
                    assertTrue(it.text.contains("ATP stores chemical energy."))
                    assertFalse(it.text.contains("CONTROL_PAGE"))
                    android.os.ParcelFileDescriptor.open(File(it.pdfPath), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { selected -> assertEquals(1, selected.pageCount) }
                    }
                }
                override suspend fun crop(documentId: String, pageIndex: Int, bounds: SourceBounds) = extraction.crop(documentId, pageIndex, bounds)
            }
            val prompt = base.assets.open("native-page-draft.md").bufferedReader().use { it.readText() }
            val engineer = LocalPageEngineer(profiles, checkedExtraction, prompt, pages::stageFidelity)
            var calls = 0
            val coordinator = EngineeringCoordinator(object : PageEngineer {
                override suspend fun engineer(request: EngineeringRequest): SourcePage { calls++; return engineer.engineer(request) }
            }, pages)
            val request = EngineeringRequest("live-selected-page", document.id, 1, checksum, 1)
            val page = coordinator.ensure(request)
            assertEquals(1, page.pageIndex)
            assertEquals(EngineeringStatus.UNCHECKED, page.status)
            assertTrue(page.corrections.isEmpty())
            val text = page.elements.joinToString("\n") { it.originalText }
            assertTrue("ATP sentence was not preserved", text.contains("ATP stores chemical energy."))
            assertTrue("DNA sentence was not preserved", text.contains("DNA carries genetic information."))
            assertFalse(text.contains("CONTROL_PAGE"))
            val figure = page.elements.firstOrNull { it.kind == SourceElementKind.FIGURE }
            assertNotNull("Original figure was not identified", figure)
            val image = BitmapFactory.decodeFile(File(File(context.filesDir, "source-assets"), figure!!.mediaId!!).path)
            try {
                var redPixels = 0
                for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                    val pixel = image.getPixel(x, y)
                    if (Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) < 60) redPixels++
                }
                assertTrue("Cropped original figure has no red source pixels", redPixels > 100)
            } finally { image.recycle() }
            assertEquals(page, coordinator.ensure(request)); assertEquals(1, calls)
            val recovered = RoomPageRepository(database.pages()).apply { load() }
            assertEquals(page, recovered.find(PageCacheKey(checksum, document.id, 1, 1)))
            assertNotNull(recovered.fidelity(document.id, 1, checksum))
            assertTrue(File(context.cacheDir, "selected-pages").listFiles().orEmpty().isEmpty())
        } finally { database.close(); root.deleteRecursively() }
    }
}
