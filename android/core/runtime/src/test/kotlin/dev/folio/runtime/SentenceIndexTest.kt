package dev.folio.runtime

import dev.folio.model.activity.ActivityData
import dev.folio.model.source.*
import dev.folio.runtime.reading.*
import dev.folio.runtime.interaction.LearningInteraction
import org.junit.Assert.*
import org.junit.Test

class SentenceIndexTest {
    @Test fun rangesPreserveTextAndIdentitySurvivesUnrelatedParagraphInsertion() {
        val paragraph = SourceElement("p", SourceElementKind.PARAGRAPH, "  ATP is 3.5 units. DNA remains intact!  ", 1)
        val page = SourcePage("doc", 0, "Page", listOf(paragraph))
        val index = SentenceIndex().compile(page)
        assertTrue(index.size >= 2)
        index.forEach { assertEquals(paragraph.originalText.substring(it.start, it.end), it.text) }
        assertEquals(index, SentenceIndex().compile(page.copy(elements = listOf(
            SourceElement("other", SourceElementKind.HEADING, "Added heading", 0), paragraph))))
        assertTrue(index.all { it.source.elementId == "p" })
        assertNotEquals(index.first().id, SentenceIndex().compile(page.copy(pageIndex = 1)).first().id)
    }
    @Test fun correctionsAreSeparateAndChangeTargetIdentityWithoutMutatingOriginal() {
        val paragraph = SourceElement("p", SourceElementKind.PARAGRAPH, "Wrong word.", 0)
        val page = SourcePage("doc", 0, "Page", listOf(paragraph))
        val corrected = SentenceIndex().compile(page.copy(corrections = mapOf("p" to "Correct word."))).single()
        assertTrue(corrected.usesCorrection)
        assertNotEquals(SentenceIndex().compile(page).single().id, corrected.id)
        assertEquals("Wrong word.", paragraph.originalText)
    }
    @Test fun focusUsesOwnedSentenceAndPreservesNotesWhileUnknownTargetsAreRejected() {
        val initial = TestFixture.reader()
        val activity = initial.activities.getValue("read")
        val before = initial.copy(activities = initial.activities + ("read" to activity.copy(data =
            (activity.data as ActivityData.Reader).copy(notes = mapOf("p1" to "Keep this note")))))
        val sentence = SentenceIndex().compile(TestFixture.catalog.page("doc", 0)!!).single()
        val focused = ReaderSentenceFocus(TestFixture.catalog).apply(before, LearningInteraction.FocusSentence("read", sentence.id))
        assertEquals(sentence.id, (focused.activities.getValue("read").data as ActivityData.Reader).selectedSentence)
        assertEquals(sentence.source, focused.context.source)
        assertEquals("read", focused.context.focusedActivityId)
        assertEquals("Keep this note", (focused.activities.getValue("read").data as ActivityData.Reader).notes["p1"])
        val otherPage = SentenceIndex().compile(TestFixture.catalog.page("doc", 1)!!).single()
        assertTrue(runCatching { ReaderSentenceFocus(TestFixture.catalog).apply(before, LearningInteraction.FocusSentence("read", otherPage.id)) }.isFailure)
    }
}
