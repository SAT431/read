package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.search.StudySearchScreen
import dev.folio.library.notes.SavedNotesScreen
import dev.folio.local.database.FolioDatabase
import dev.folio.model.activity.ActivityData
import dev.folio.model.command.AppCommand
import dev.folio.model.port.PageCacheKey
import dev.folio.model.search.SearchKind
import dev.folio.model.source.*
import dev.folio.model.workspace.AppDestination
import dev.folio.runtime.search.StudySearchSession
import com.example.read.fixture.ArchitectureSources
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class StudySearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun nativeBackgroundSearchAtLargeTextPagesOpensSavedNoteAndCachedPassageWithoutChangingLearningRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val file = File(context.cacheDir, "search-ui-${UUID.randomUUID()}.db")
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val fixture = ColdSourceFixture(database)
            val seed = fixture.sampleState(); val reader = seed.activities.getValue("reader").data as ActivityData.Reader
            val notes = (0..44).associate { "search-note-$it" to SourceAnnotation("search-note-$it", reader.annotations.values.first().source,
                kind = AnnotationKind.NOTE, text = "Labelled notes ${it.toString().padStart(2, '0')}") }
            val original = seed.copy(activities = seed.activities + ("reader" to seed.activities.getValue("reader").copy(data = reader.copy(annotations = reader.annotations + notes))),
                navigation = seed.navigation.copy(destination = AppDestination.SEARCH, globalQuery = "Labelled notes", searchKind = SearchKind.NOTE))
            fixture.repository.save(original); fixture.session.attachWriter(scope); fixture.session.restore()
            val page = requireNotNull(fixture.warm.page(ArchitectureSources.DOCUMENT_ID, 0)).let { it.copy(isFixture = false,
                elements = it.elements.map { e -> if (e.id == "cell-p1") e.copy(originalText = "Labelled search passage.") else e }) }
            fixture.pages.save(PageCacheKey(fixture.checksum, page.documentId, 0, page.engineeringVersion), page)
            val search = StudySearchSession(scope, fixture.session.state, fixture.library, fixture.catalog, fixture.pages.revision, fixture.curriculum)
            search.awaitCurrent(fixture.session.state.value)
            compose.setContent { FolioTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                    val state by fixture.session.state.collectAsState()
                    val results by search.state.collectAsState()
                    val ready = results.takeIf { search.snapshotFor(state) == it }
                    if (state.navigation.destination == AppDestination.NOTES) SavedNotesScreen(state, fixture.catalog) { runBlocking { fixture.act(it) } }
                    else StudySearchScreen(state, ready) { runBlocking { fixture.act(it) } }
                }
            } }
            val list = compose.onNodeWithTag("study-search")
            list.performScrollToNode(hasTestTag("search-next")); compose.onNodeWithTag("search-next").assertIsEnabled().performClick()
            search.awaitCurrent(fixture.session.state.value)
            val results = search.snapshotFor(fixture.session.state.value)!!; assertEquals(1, results.page); assertEquals(5, results.results.size)
            val selected = results.results.last()
            list.performScrollToNode(hasTestTag("search-open-${selected.fingerprint}"))
            compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("study-search-large-text.png")
            compose.onNodeWithTag("search-open-${selected.fingerprint}").assertIsDisplayed().performClick()
            compose.onNodeWithTag("saved-notes").assertIsDisplayed()
            val noteList = compose.onNodeWithTag("saved-notes")
            val noteText = hasText(selected.excerpt) and hasSetTextAction().not()
            noteList.performScrollToNode(noteText); compose.onNode(noteText).assertIsDisplayed()
            fixture.act(AppCommand.SearchStudy("Labelled search passage", SearchKind.PASSAGE)); search.awaitCurrent(fixture.session.state.value)
            val passage = search.snapshotFor(fixture.session.state.value)!!.results.single()
            compose.onNodeWithTag("study-search").performScrollToNode(hasTestTag("search-open-${passage.fingerprint}"))
            compose.onNodeWithTag("search-open-${passage.fingerprint}").performClick()
            val after = fixture.session.state.value
            assertEquals(AppDestination.WORKSPACE, after.navigation.destination)
            assertEquals(SourceAnchor(page.documentId, 0, "cell-p1"), after.context.source)
            assertEquals(original.activities.getValue("cached-quiz"), after.activities.getValue("cached-quiz"))
            val before = original.activities.getValue("reader").data as ActivityData.Reader
            val current = after.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(before.annotations, current.annotations); assertEquals(before.annotationDrafts, current.annotationDrafts)
            assertEquals(before.bookmarks, current.bookmarks); assertEquals(before.readingConfirmations, current.readingConfirmations)
            fixture.session.flush(); assertEquals(after, fixture.repository.load())
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); context.deleteDatabase(file.absolutePath) }
    }
}
