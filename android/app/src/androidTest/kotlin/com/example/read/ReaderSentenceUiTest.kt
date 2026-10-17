package com.example.read

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.source.*
import dev.folio.study.reader.ReadingCompiler
import dev.folio.study.reader.SentenceText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderSentenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun sentenceLinksFocusExactSourceAtNormalAndLargeReadingSizes() {
        val original = "Cells contain organelles. Their compartments support different processes."
        val page = SourcePage("sentence-ui-fixture", 0, "Reader fixture", listOf(
            SourceElement("paragraph", SourceElementKind.PARAGRAPH, original, 0)), isFixture = true)
        val block = ReadingCompiler().compile(page).single()
        assertEquals(2, block.sentences.size)
        val selected = mutableStateOf<String?>(null)
        val size = mutableStateOf(18)
        val focused = mutableListOf<String>()
        compose.setContent { FolioTheme(false) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                SelectionContainer {
                    SentenceText(block, size.value, selected.value) { focused.add(it); selected.value = it }
                }
            }
        } }
        val passage = compose.onNodeWithTag("passage-paragraph")
        fun tapSentence(index: Int) {
            val layouts = mutableListOf<TextLayoutResult>()
            passage.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val point = layouts.single().getBoundingBox(block.sentences[index].start + 2).center
            passage.performTouchInput { click(point) }
            compose.runOnIdle { assertEquals(block.sentences[index].id, selected.value) }
        }
        tapSentence(0)
        compose.runOnIdle { size.value = 36 }
        tapSentence(1)
        passage.assertTextEquals(original)
        compose.runOnIdle {
            assertEquals(block.sentences.map { it.id }, focused)
            assertTrue(block.sentences.all { it.source.documentId == page.documentId && it.source.elementId == "paragraph" })
        }
    }
}
