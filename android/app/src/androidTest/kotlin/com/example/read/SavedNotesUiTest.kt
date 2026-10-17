package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.notes.SavedNotesScreen
import dev.folio.local.database.FolioDatabase
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.AppCommand
import dev.folio.model.source.NotesFilter
import dev.folio.model.workspace.AppDestination
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SavedNotesUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun unavailableDraftAtLargeTextCanBeEditedRetainedAndSavedWithoutRetargetingOrLosingStudyData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = File(context.cacheDir, "saved-notes-${UUID.randomUUID()}.db")
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, path.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val fixture = ColdSourceFixture(database)
            val original = fixture.sampleState().let { it.copy(navigation = it.navigation.copy(destination = AppDestination.NOTES)) }
            fixture.repository.save(original); fixture.session.attachWriter(scope); fixture.session.restore()
            assertNull(fixture.session.storageError.value)
            compose.setContent { FolioTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                    val state by fixture.session.state.collectAsState()
                    SavedNotesScreen(state, fixture.catalog) { runBlocking { fixture.act(it) } }
                }
            } }
            val list = compose.onNodeWithTag("saved-notes")
            list.performScrollToNode(hasTestTag("notes-open-reader-saved-note"))
            compose.onNodeWithTag("notes-open-reader-saved-note").assertIsNotEnabled()
            list.performScrollToIndex(0)
            compose.onNodeWithTag("notes-filter-DRAFTS").performClick()
            list.performScrollToNode(hasText("Edit private draft")); compose.onNodeWithText("Edit private draft").performClick()
            compose.onNodeWithTag("notes-draft-editor").performTextReplacement("Labelled edited private draft")
            compose.onNodeWithTag("notes-save-draft").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-draft-editor").fetchSemanticsNodes().isEmpty() }
            list.performScrollToNode(hasText("Labelled edited private draft")); compose.onNodeWithText("Labelled edited private draft").assertIsDisplayed()
            list.performScrollToNode(hasText("Discard draft…")); compose.onNodeWithText("Discard draft…").performClick()
            compose.onNodeWithText("Keep draft").performClick()
            compose.runOnIdle { assertEquals(1, (fixture.session.state.value.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts.size) }
            list.performScrollToNode(hasTestTag("notes-commit-reader-saved-draft"))
            compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("saved-notes-large-text.png")
            compose.onNodeWithTag("notes-commit-reader-saved-draft").assertIsEnabled().performClick()
            val state = fixture.session.state.value
            val before = original.activities.getValue("reader").data as ActivityData.Reader
            val after = state.activities.getValue("reader").data as ActivityData.Reader
            assertTrue(after.annotationDrafts.isEmpty()); assertEquals(before.bookmarks, after.bookmarks); assertEquals(before.readingConfirmations, after.readingConfirmations)
            assertEquals(before.annotations.getValue("saved-note"), after.annotations.getValue("saved-note"))
            val saved = after.annotations.values.single { it.id != "saved-note" }
            assertEquals(before.annotationDrafts.getValue("saved-draft").source, saved.source); assertEquals("Labelled edited private draft", saved.text)
            assertEquals(original.activities.getValue("cached-quiz"), state.activities.getValue("cached-quiz"))
            // Deleting the original note keeps its separate private edit; recover it without retargeting.
            val originalNote = after.annotations.getValue("saved-note")
            fixture.act(AppCommand.BeginSavedNoteEdit("reader", originalNote.id, dev.folio.runtime.reading.SavedNotes.fingerprint("reader", originalNote)))
            fixture.act(AppCommand.RemoveAnnotation("reader", originalNote.id, originalNote.text))
            list.performScrollToNode(hasTestTag("notes-copy-reader-edit-saved-note"))
            compose.onNodeWithTag("notes-copy-reader-edit-saved-note").assertIsEnabled().performClick()
            val final = fixture.session.state.value
            val recovered = final.activities.getValue("reader").data as ActivityData.Reader
            assertTrue(recovered.annotationDrafts.isEmpty()); assertFalse("saved-note" in recovered.annotations)
            val copy = recovered.annotations.values.single { it.text == originalNote.text }
            assertEquals(originalNote.source, copy.source); assertNotEquals(originalNote.id, copy.id)
            fixture.session.flush(); assertEquals(final, fixture.repository.load())

        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); context.deleteDatabase(path.absolutePath) }
    }
}
