package dev.folio.study.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.source.SourceElementKind
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.interaction.LearningInteraction
import dev.folio.ui.component.*
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.folio.ui.media.LocalSourceRevision

class ReaderRenderer : RegisteredRenderer {
    override val kind = ComponentKind.READER
    @Composable override fun Render(context: ComponentRenderContext) {
        val sourceRevision = LocalSourceRevision.current
        val page = context.catalog.page(context.activity.source.documentId, context.activity.source.pageIndex)
        val data = context.activity.data as ActivityData.Reader
        ReadingBookmarkShelf(context.activity.id, context.activity.source, data, context.catalog, context.onInteraction)
        ReadingCheckpointShelf(context.activity.id, data, context.catalog, context.onInteraction)
        if (page == null) {
            Text("Readable text is not available for this page. Your saved bookmarks and reading checkpoints remain available.",
                style = MaterialTheme.typography.bodyMedium)
            return
        }
        val blocks = remember(page) { ReadingCompiler().compile(page) }
        val progress = remember(page, data.readingConfirmations, sourceRevision) {
            dev.folio.runtime.reading.ReadingConfirmations.progress(data, page, context.catalog)
        }
        val scroll = key(page.documentId, page.pageIndex) { rememberLazyListState(data.scrollIndex, data.scrollOffset) }
        val latestData by rememberUpdatedState(data)
        LaunchedEffect(context.activity.id, page.documentId, page.pageIndex) {
            snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }
                .distinctUntilChanged().collect { (index, offset) ->
                    if (latestData.scrollIndex != index || latestData.scrollOffset != offset) context.onInteraction(
                        LearningInteraction.SetReadingPosition(context.activity.id, index, offset))
                }
        }
        LaunchedEffect(data.selectedSentence, page, context.activity.source.elementId) {
            val sentenceTarget = blocks.indexOfFirst { block -> block.sentences.any { it.id == data.selectedSentence } }
            val target = if (sentenceTarget >= 0) sentenceTarget else blocks.indexOfFirst { it.source.id == context.activity.source.elementId }
            if (target >= 0 && scroll.layoutInfo.visibleItemsInfo.none { it.index == target }) scroll.scrollToItem(target)
        }
        Text(if (page.isFixture) "Architecture fixture · original sample, not NCERT" else "Source reconstruction · ${page.status}",
            style = MaterialTheme.typography.labelSmall)
        Text(page.title, style = MaterialTheme.typography.headlineLarge)
        ReadingProgressIndicator(context.activity.id, progress)
        LaunchedEffect(page.documentId, page.pageIndex, data.bookmarkJump) {
            if (data.bookmarkJump > 0 && blocks.isNotEmpty()) scroll.scrollToItem(data.scrollIndex.coerceIn(blocks.indices), data.scrollOffset)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), state = scroll,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(blocks, key = { it.source.id }) { block ->
                when (block.source.kind) {
                    SourceElementKind.HEADING -> Text(block.displayedText, style = MaterialTheme.typography.headlineMedium)
                    SourceElementKind.FIGURE -> OriginalFigureBlock(block.source, context.media)
                    else -> {
                        val anchor = context.activity.source.copy(elementId = block.source.id)
                        val annotations = data.annotations.values.filter { it.source == anchor }
                        ParagraphBlock(block, context.preferences.readingSizeSp,
                        context.activity.source.elementId == block.source.id, data.selectedSentence,
                        { context.onInteraction(LearningInteraction.FocusSentence(context.activity.id, it)) }, annotations) {
                        context.onCommands(listOf(WorkspaceCommand.FocusSource(context.activity.source.copy(elementId = block.source.id))))
                    }
                        if (block.source.kind in setOf(SourceElementKind.PARAGRAPH, SourceElementKind.CAPTION, SourceElementKind.LIST)) {
                            if (block.displayedText.isNotBlank()) GuidedReadingMarker(context.activity.id, anchor,
                                block.sentences.firstOrNull { it.id == data.selectedSentence }?.id, data, progress, context.catalog, context.onInteraction)
                            ReadingBookmarkMarker(context.activity.id, anchor, block.sentences.firstOrNull { it.id == data.selectedSentence }?.id,
                                data, context.onInteraction)
                            SourceAnnotationPanel(context.activity.id, anchor, block.sentences, data.selectedSentence, annotations,
                                data.annotationDrafts, context.onInteraction)
                        }
                    }
                }
            }
        }
    }
}
