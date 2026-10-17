package dev.folio.runtime.engineering

import java.util.Locale
import kotlinx.serialization.json.*

/** Multiset differences flag things to inspect; they cannot establish wording or reading-order fidelity. */
class FidelityComparison {
    fun compare(evidence: String, reconstruction: String, uncertainties: List<String>): JsonObject {
        require(evidence.length <= 100_000 && reconstruction.length <= 2_000_000)
        require(uncertainties.size <= 300 && uncertainties.sumOf { it.length } <= 100_000)
        fun tokens(text: String) = Regex("[\\p{L}\\p{N}_]+").findAll(text)
            .map { it.value.lowercase(Locale.ROOT) }.groupingBy { it }.eachCount()
        val original = tokens(evidence)
        val model = tokens(reconstruction)
        val available = original.isNotEmpty()
        fun differences(left: Map<String, Int>, right: Map<String, Int>) = left.mapNotNull { (word, count) ->
            (count - (right[word] ?: 0)).takeIf { it > 0 }?.let { word to it }
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        val missing = if (available) differences(original, model) else emptyList()
        val additional = if (available) differences(model, original) else emptyList()
        fun examples(values: List<Pair<String, Int>>) = JsonArray(values.take(50).map { (token, count) -> buildJsonObject {
            put("token", token.take(120)); put("count", count)
        } })
        return buildJsonObject {
            put("missingEvidenceTokenCount", if (available) JsonPrimitive(missing.sumOf { it.second }) else JsonNull)
            put("additionalModelTokenCount", if (available) JsonPrimitive(additional.sumOf { it.second }) else JsonNull)
            put("deterministicTextAvailable", available)
            put("missingEvidenceTokens", examples(missing)); put("additionalModelTokens", examples(additional))
            put("tokenExamplesLimited", missing.size > 50 || additional.size > 50 || (missing + additional).any { it.first.length > 120 })
            put("uncertainties", JsonArray(uncertainties.map(::JsonPrimitive)))
            put("comparisonNote", "Token comparison does not verify wording, punctuation or reading order. A scan may have no embedded PDF text; compare the original image.")
        }
    }
}
