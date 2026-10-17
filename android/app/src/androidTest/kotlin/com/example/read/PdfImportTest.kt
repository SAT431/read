package com.example.read

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.library.LocalPdfLibrary
import dev.folio.local.pdf.PdfImporter
import dev.folio.local.pdf.NativePdfTools
import dev.folio.model.port.SourceCatalog
import dev.folio.model.source.*
import android.graphics.BitmapFactory
import dev.folio.local.database.FolioDatabase
import androidx.room.Room
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class PdfImportTest {
    @Test fun originalPdfImportIsRealAndDuplicateImportsPreserveLibrary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "pdf-test-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(root, "input.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(400, 600, 1).create())
            page.canvas.drawText("Synthetic PDF import test", 40f, 60f, android.graphics.Paint())
            document.finishPage(page); file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, File(root, "test.db").absolutePath).build()
        val library = LocalPdfLibrary(root, database.library())
        val importer = PdfImporter(context.contentResolver, root, library)
        val first = importer.import(Uri.fromFile(file), "chapter", "Input.pdf")
        val second = importer.import(Uri.fromFile(file), "chapter", "Input.pdf")
        assertEquals(first.id, second.id); assertEquals(1, first.document.pageCount)
        val recovered = LocalPdfLibrary(root, database.library()); recovered.load()
        assertEquals(first, recovered.entries.value.single())
        val catalog = object : SourceCatalog {
            override fun documents() = listOf(first.document)
            override fun chapters() = emptyList<Chapter>()
            override fun page(documentId: String, pageIndex: Int): SourcePage? = null
            override fun contains(anchor: SourceAnchor) = false
        }
        val renderer = NativePdfTools(root, catalog)
        val renders = List(5) { async(Dispatchers.Default) { renderer.render(first.document.id, 0) } }.awaitAll()
        assertEquals(1, renders.toSet().size)
        val bitmap = BitmapFactory.decodeFile(File(root, "source-assets/${renders.first()}").path)
        assertNotNull(bitmap)
        assertEquals(1200, bitmap.width); assertEquals(1800, bitmap.height)
        bitmap.recycle()
        database.close()
    }
}
