package dev.folio.study.reader

import dev.folio.model.source.SourceElement
import dev.folio.model.source.SourcePage
import dev.folio.model.source.SourceSentence
import dev.folio.runtime.reading.SentenceIndex

data class ReadingBlock(val source: SourceElement, val displayedText: String, val sentences: List<SourceSentence> = emptyList())

/** Pure source-to-view model compiler. Enrichment cannot overwrite source text. */
class ReadingCompiler {
    fun compile(page: SourcePage): List<ReadingBlock> {
        require(page.elements.map { it.id }.distinct().size == page.elements.size) { "Duplicate source element" }
        val sentences = SentenceIndex().compile(page).groupBy { it.source.elementId }
        return page.elements.sortedBy { it.order }.map {
            ReadingBlock(it, page.corrections[it.id] ?: it.originalText, sentences[it.id].orEmpty())
        }
    }
}
