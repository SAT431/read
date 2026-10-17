package dev.folio.study.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

/** Tolerates older count-only cache reports without inventing missing evidence. */
@Composable
internal fun FidelityDetails(report: JsonObject?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Extraction comparison", style = MaterialTheme.typography.titleMedium)
        if (report == null) Text("No comparison report is available for this cached page.")
        else {
            val available = (report["deterministicTextAvailable"] as? JsonPrimitive)?.booleanOrNull == true
            if (!available) Text("No usable embedded PDF text. Compare the reconstruction with the original image.")
            else {
                val missing = (report["missingEvidenceTokenCount"] as? JsonPrimitive)?.intOrNull
                val additional = (report["additionalModelTokenCount"] as? JsonPrimitive)?.intOrNull
                Text("PDF text comparison: ${missing ?: "unknown"} missing · ${additional ?: "unknown"} additional tokens")
                TokenExamples("Missing from reconstruction", report["missingEvidenceTokens"] as? JsonArray)
                TokenExamples("Additional in reconstruction", report["additionalModelTokens"] as? JsonArray)
                if ((report["tokenExamplesLimited"] as? JsonPrimitive)?.booleanOrNull == true) Text("Token examples shortened; up to 50 in each list.")
            }
            val uncertainties = report["uncertainties"] as? JsonArray
            Text("Reported uncertainties: ${uncertainties?.size ?: "unknown"}")
            uncertainties.orEmpty().take(300).forEach { item -> (item as? JsonPrimitive)?.contentOrNull?.let { Text("• $it") } }
        }
        Text("Matching token counts do not verify wording, punctuation or reading order. Reconstruction remains unchecked.")
    }
}

@Composable
private fun TokenExamples(title: String, values: JsonArray?) {
    val text = values.orEmpty().take(50).mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val token = (item["token"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val count = (item["count"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        if (count <= 0) null else "${token.take(120)} × $count"
    }.joinToString(", ")
    if (text.isNotEmpty()) Text("$title: $text")
}
