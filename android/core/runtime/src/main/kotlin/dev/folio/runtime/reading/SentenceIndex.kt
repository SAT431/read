package dev.folio.runtime.reading

import dev.folio.model.source.*
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale

/** Deterministic reading targets, not a claim of semantic or transcription accuracy. */
class SentenceIndex {
    fun compileElement(page: SourcePage, elementId: String): List<SourceSentence> = page.elements.firstOrNull {
        it.id == elementId && it.kind in setOf(SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST)
    }?.let { compile(page, it) }.orEmpty()
    fun compile(page: SourcePage): List<SourceSentence> = page.elements.sortedBy { it.order }.flatMap { element ->
        if (element.kind !in setOf(SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST)) emptyList()
        else compile(page, element)
    }
    private fun compile(page: SourcePage, element: SourceElement): List<SourceSentence> {
        val corrected = element.id in page.corrections
        val text = page.corrections[element.id] ?: element.originalText
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(text) }
        val result = mutableListOf<SourceSentence>()
        var boundaryStart = iterator.first()
        var boundaryEnd = iterator.next()
        while (boundaryEnd != BreakIterator.DONE) {
            var start = boundaryStart
            var end = boundaryEnd
            while (start < end && text[start].isWhitespace()) start++
            while (end > start && text[end - 1].isWhitespace()) end--
            if (start < end) {
                val words = text.substring(start, end)
                val identity = "${page.documentId.length}:${page.documentId}:${page.pageIndex}:${element.id.length}:${element.id}:$start:$end:${words.length}:$words"
                val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                result.add(SourceSentence("sentence-$hash", SourceAnchor(page.documentId, page.pageIndex, element.id), start, end, words, corrected))
            }
            boundaryStart = boundaryEnd
            boundaryEnd = iterator.next()
        }
        return result
    }
}
