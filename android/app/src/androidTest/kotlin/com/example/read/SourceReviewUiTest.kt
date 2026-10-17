package com.example.read

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.pdf.OriginalPdfPage
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.local.media.LocalMediaRepository
import dev.folio.local.pdf.NativePdfTools
import dev.folio.model.command.AppCommand
import dev.folio.model.library.*
import dev.folio.model.source.*
import dev.folio.model.workspace.*
import dev.folio.runtime.action.LibraryNavigationReducer
import dev.folio.runtime.action.SourceReviewNavigation
import dev.folio.runtime.engineering.FidelityComparison
import dev.folio.study.review.SourceReviewScreen
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun originalPdfComparisonShowsDifferencesAndScanLimitsWithoutApprovingContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "review-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = File(File(root, "pdfs").apply { mkdirs() }, "sample.pdf")
            val original = PdfDocument()
            try {
                repeat(2) { index ->
                    val page = original.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    if (index == 0) page.canvas.drawText("ATP DNA", 30f, 80f, Paint().apply { color = Color.BLACK; textSize = 20f })
                    else page.canvas.drawRect(40f, 80f, 180f, 200f, Paint().apply { color = Color.BLUE })
                    original.finishPage(page)
                }
                file.outputStream().use(original::writeTo)
            } finally { original.close() }
            val document = SourceDocument("review-doc", "Review fixture", 2, "fixture-checksum", file.path)
            val pages = (0..1).map { index -> SourcePage(document.id, index, "Sample reconstruction", listOf(
                SourceElement("p$index", SourceElementKind.PARAGRAPH, if (index == 0) "ATP RNA" else "Image-only reconstruction fixture", 0,
                    if (index == 0) SourceBounds(25f, 55f, 140f, 90f) else null)), widthPoints = 300f, heightPoints = 400f) }
            val catalog = InMemorySourceCatalog(listOf(document), emptyList(), pages)
            val tools = NativePdfTools(root, catalog)
            val media = LocalMediaRepository(File(root, "source-assets"))
            val curriculum = Neet2026Catalog()
            val pdf = LibraryPdf("review-pdf", curriculum.chapters().first().id, document, 0)
            val reducer = LibraryNavigationReducer(curriculum)
            val regions = SourceReviewNavigation(catalog, reducer)
            val state = mutableStateOf(requireNotNull(reducer.reduce(Workspace(), AppCommand.ReviewPage(document.id, 0), listOf(pdf))))
            val compact = mutableStateOf(false)
            fun dispatch(command: AppCommand) { state.value = if (command is AppCommand.ReviewRegion) regions.apply(state.value, command, listOf(pdf))
                else requireNotNull(reducer.reduce(state.value, command, listOf(pdf))) }
            compose.setContent { FolioTheme(false) {
                Column(Modifier.fillMaxSize()) {
                    TextButton({ compact.value = !compact.value }) { Text("Toggle compact review") }
                    Box(Modifier.weight(1f).then(if (compact.value) Modifier.width(420.dp) else Modifier.fillMaxWidth())) {
                        val index = state.value.navigation.pdfPageIndex
                        val report = FidelityComparison().compare(if (index == 0) "ATP DNA" else "", pages[index].elements.single().originalText,
                            if (index == 0) listOf("Check the label") else emptyList())
                        if (state.value.navigation.sourceReviewVisible) SourceReviewScreen(pdf, state.value.navigation, pages[index], report, media, ::dispatch) { region ->
                            OriginalPdfPage(pdf, state.value.navigation, tools, media, ::dispatch, region)
                        } else OriginalPdfScreen(pdf, state.value.navigation, tools, media, ::dispatch)
                    }
                }
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Original PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Missing from reconstruction: dna × 1").assertExists()
            compose.onNodeWithTag("reconstruction-review").performScrollToNode(hasTestTag("review-element-p0"))
            compose.onNodeWithTag("review-element-p0").assertTextEquals("ATP RNA")
            compose.onNodeWithTag("reconstruction-review").performScrollToNode(hasText("Show original region"))
            compose.onNodeWithText("Show original region").performClick()
            compose.onNodeWithContentDescription("Original source region p0, page 1").assertExists()
            compose.runOnIdle { assertEquals("p0", state.value.navigation.reviewElementId) }
            compose.onNodeWithText("Toggle compact review").performClick()
            compose.onNodeWithTag("review-original").assertIsDisplayed()
            compose.onNodeWithContentDescription("Original source region p0, page 1").assertExists()
            compose.onNodeWithText("Clear region").performClick()
            compose.onNodeWithTag("original-region-overlay").assertDoesNotExist()
            compose.onNodeWithTag("reconstruction-review").performScrollToNode(hasText("Show original region"))
            compose.onNodeWithText("Show original region").performClick()
            compose.onNodeWithText("Next page").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Original PDF page 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("No usable embedded PDF text. Compare the reconstruction with the original image.").assertExists()
            compose.onNodeWithTag("original-region-overlay").assertDoesNotExist()
            compose.onNodeWithText("Open original").performClick()
            compose.onNodeWithText("Original PDF · page 2 of 2").assertExists()
            compose.runOnIdle {
                assertFalse(state.value.navigation.sourceReviewVisible)
                assertEquals(1, state.value.navigation.pdfPageIndex)
                assertTrue(pages.all { it.status == EngineeringStatus.UNCHECKED && it.corrections.isEmpty() })
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
