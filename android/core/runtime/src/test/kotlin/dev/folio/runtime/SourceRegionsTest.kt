package dev.folio.runtime

import dev.folio.model.source.*
import dev.folio.runtime.engineering.SourceRegions
import org.junit.Assert.*
import org.junit.Test

class SourceRegionsTest {
    private val source = SourceAnchor("doc", 0, "p")
    private val bounds = SourceBounds(20f, 10f, 60f, 30f)
    private val page = SourcePage("doc", 0, "Page", listOf(SourceElement("p", SourceElementKind.PARAGRAPH,
        "Text", 0, bounds)), widthPoints = 200f, heightPoints = 100f)
    private val region = requireNotNull(SourceRegions.resolve(page, source))
    private fun assertBounds(expected: SourceBounds, actual: SourceBounds?) {
        requireNotNull(actual)
        assertEquals(expected.left, actual.left, .001f); assertEquals(expected.top, actual.top, .001f)
        assertEquals(expected.right, actual.right, .001f); assertEquals(expected.bottom, actual.bottom, .001f)
    }
    @Test fun centeredFitProjectsIntoLetterboxedViewportWithoutStretching() {
        assertBounds(SourceBounds(40f, 120f, 120f, 160f), SourceRegions.project(region, 1000f, 500f, 400f, 400f))
        assertBounds(SourceBounds(220f, 10f, 260f, 30f), SourceRegions.project(region, 1000f, 500f, 600f, 100f))
    }
    @Test fun fullPageRegionMatchesFittedImageRectangle() {
        val full = region.copy(bounds = SourceBounds(0f, 0f, 200f, 100f))
        assertBounds(SourceBounds(0f, 100f, 400f, 300f), SourceRegions.project(full, 1000f, 500f, 400f, 400f))
    }
    @Test fun wrongPageUnknownElementMissingDimensionsAndOutOfBoundsNeverInventRegions() {
        assertNull(SourceRegions.resolve(page, source.copy(documentId = "other")))
        assertNull(SourceRegions.resolve(page, source.copy(pageIndex = 1)))
        assertNull(SourceRegions.resolve(page, source.copy(elementId = "unknown")))
        assertNull(SourceRegions.resolve(page.copy(widthPoints = null), source))
        assertNull(SourceRegions.resolve(page.copy(elements = page.elements.map { it.copy(bounds = bounds.copy(right = 201f)) }), source))
        assertNull(SourceRegions.resolve(page.copy(elements = page.elements.map { it.copy(bounds = null) }), source))
    }
    @Test fun mismatchedRotationInvalidDimensionsAndInvalidRegionsNeverDrawMisleadingBoxes() {
        assertNull(SourceRegions.project(region, 500f, 1000f, 400f, 400f))
        assertNull(SourceRegions.project(region, 1000f, 500f, 0f, 400f))
        assertNull(SourceRegions.project(region, Float.NaN, 500f, 400f, 400f))
        assertNull(SourceRegions.project(region.copy(bounds = bounds.copy(left = -1f)), 1000f, 500f, 400f, 400f))
    }
}
