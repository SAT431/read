package com.example.read

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.pdf.OriginalPdfPage
import dev.folio.model.library.LibraryPdf
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.AppNavigation
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OriginalRegionViewportTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun regionPixelsUseCenteredFitAndShareImageZoomPanWhileMismatchedAxesHideOverlay() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bytes = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
            finally { bitmap.recycle() }
        val media = object : MediaRepository { override suspend fun read(mediaId: String) = bytes }
        val tools = object : PdfTools {
            override suspend fun render(documentId: String, pageIndex: Int) = "white.png"
            override suspend fun crop(documentId: String, pageIndex: Int, bounds: SourceBounds) = error("Not used")
        }
        val pdf = LibraryPdf("fixture", "chapter", SourceDocument("doc", "Region fixture", 2, "hash", null), 0)
        val navigation = mutableStateOf(AppNavigation(pdfId = pdf.id))
        val region = mutableStateOf<SourceRegion?>(SourceRegion(SourceAnchor("doc", 0, "p"), SourceBounds(60f, 20f, 100f, 40f), 200f, 100f))
        compose.setContent { FolioTheme(false) {
            Column {
                TextButton({ navigation.value = navigation.value.copy(pdfZoom = 2f, pdfPanX = 20f, pdfPanY = 15f) }) { Text("Transform fixture") }
                Box(Modifier.size(400.dp)) { OriginalPdfPage(pdf, navigation.value, tools, media, {}, region.value) }
            }
        } }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("original-region-overlay").fetchSemanticsNodes().isNotEmpty() }
        fun assertPixel(fractionX: Float, fractionY: Float, offsetX: Float = 0f, offsetY: Float = 0f, tinted: Boolean) {
            val pixels = compose.onNodeWithTag("original-pdf-viewport").captureToImage().toPixelMap()
            val color = pixels[(pixels.width * fractionX + offsetX).toInt(), (pixels.height * fractionY + offsetY).toInt()]
            val white = color.red > .98f && color.green > .98f && color.blue > .98f
            assertEquals("Unexpected overlay pixel at $fractionX,$fractionY", !tinted, white)
        }
        assertPixel(.4f, .4f, tinted = true)
        assertPixel(.7f, .4f, tinted = false)
        compose.onNodeWithText("Transform fixture").performClick()
        assertPixel(.3f, .3f, 20f, 15f, tinted = true)
        assertPixel(.7f, .3f, 20f, 15f, tinted = false)
        compose.runOnIdle { region.value = region.value!!.copy(pageHeightPoints = 200f) }
        compose.onNodeWithTag("original-region-overlay").assertDoesNotExist()
        compose.onNodeWithText("Original region unavailable: page dimensions differ.").assertExists()
        compose.runOnIdle { region.value = region.value!!.copy(source = SourceAnchor("doc", 1, "p")) }
        compose.onNodeWithTag("original-region-overlay").assertDoesNotExist()
        compose.onNodeWithText("Original region unavailable: page dimensions differ.").assertDoesNotExist()
    }
}
