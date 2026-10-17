package dev.folio.study.reader

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import dev.folio.model.source.*

/** One continuous selectable paragraph; annotations add focus without rewriting its text. */
@Composable
fun SentenceText(block: ReadingBlock, sizeSp: Int, selected: String?,
    annotations: List<SourceAnnotation> = emptyList(), onFocus: (String) -> Unit) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val text = buildAnnotatedString {
        var offset = 0
        block.sentences.forEach { sentence ->
            append(block.displayedText.substring(offset, sentence.start))
            withLink(LinkAnnotation.Clickable(sentence.id, TextLinkStyles(
                SpanStyle(background = if (selected == sentence.id) highlight else androidx.compose.ui.graphics.Color.Transparent))) {
                onFocus(sentence.id)
            }) { append(block.displayedText.substring(sentence.start, sentence.end)) }
            offset = sentence.end
        }
        append(block.displayedText.substring(offset))
        annotations.forEach { annotation ->
            val sentence = block.sentences.firstOrNull { it.id == annotation.sentenceId }
            if (annotation.sentenceId != null && sentence == null) return@forEach
            val start = sentence?.start ?: 0
            val end = sentence?.end ?: block.displayedText.length
            when (annotation.kind) {
                AnnotationKind.HIGHLIGHT -> addStyle(SpanStyle(background = highlight), start, end)
                AnnotationKind.UNDERLINE -> addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
                AnnotationKind.NOTE -> Unit
            }
        }
    }
    Text(text, Modifier.testTag("passage-${block.source.id}"),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.7).sp))
}
