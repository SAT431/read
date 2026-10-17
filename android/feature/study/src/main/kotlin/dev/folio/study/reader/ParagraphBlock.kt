package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folio.designsystem.primitive.FolioActionButton

@Composable
internal fun ParagraphBlock(block: ReadingBlock, sizeSp: Int, focused: Boolean, selectedSentence: String?,
    onSentence: (String) -> Unit, annotations: List<dev.folio.model.source.SourceAnnotation> = emptyList(), onFocus: () -> Unit) {
    Column(Modifier.background(if (focused) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .4f) else Color.Transparent)
        .padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer { SentenceText(block, sizeSp, selectedSentence, annotations, onSentence) }
        FolioActionButton(if (focused) "Passage selected" else "Focus passage", onFocus)
    }
}
