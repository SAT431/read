package dev.folio.runtime

import dev.folio.runtime.engineering.FidelityComparison
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FidelityComparisonTest {
    private val comparison = FidelityComparison()
    @Test fun preservesMultiplicityAndListsMissingAndAdditionalExamples() {
        val report = comparison.compare("ATP DNA DNA", "ATP RNA", listOf("Check the label"))
        assertEquals(2, report.getValue("missingEvidenceTokenCount").jsonPrimitive.int)
        assertEquals(1, report.getValue("additionalModelTokenCount").jsonPrimitive.int)
        val missing = report.getValue("missingEvidenceTokens").jsonArray.single().jsonObject
        assertEquals("dna", missing.getValue("token").jsonPrimitive.content)
        assertEquals(2, missing.getValue("count").jsonPrimitive.int)
        assertEquals("Check the label", report.getValue("uncertainties").jsonArray.single().jsonPrimitive.content)
    }
    @Test fun scanWithoutTextDoesNotLabelTranscribedWordsAsAdditions() {
        val report = comparison.compare("", "A reconstructed scan", emptyList())
        assertFalse(report.getValue("deterministicTextAvailable").jsonPrimitive.boolean)
        assertEquals(JsonNull, report.getValue("missingEvidenceTokenCount"))
        assertEquals(JsonNull, report.getValue("additionalModelTokenCount"))
        assertTrue(report.getValue("additionalModelTokens").jsonArray.isEmpty())
    }
    @Test fun zeroBagDifferencesDoNotClaimReadingOrderVerification() {
        val report = comparison.compare("ATP produces energy", "energy produces ATP", emptyList())
        assertEquals(0, report.getValue("missingEvidenceTokenCount").jsonPrimitive.int)
        assertTrue(report.getValue("comparisonNote").jsonPrimitive.content.contains("does not verify"))
        assertFalse(report.containsKey("reviewed"))
    }
    @Test fun samplesAreBoundedWhileTotalCountsRemainComplete() {
        val report = comparison.compare((1..80).joinToString(" ") { "word$it" }, "", emptyList())
        assertEquals(80, report.getValue("missingEvidenceTokenCount").jsonPrimitive.int)
        assertEquals(50, report.getValue("missingEvidenceTokens").jsonArray.size)
        assertTrue(report.getValue("tokenExamplesLimited").jsonPrimitive.boolean)
    }
}
