package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.ArchitectureSources
import dev.folio.local.database.FolioDatabase
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.*
import dev.folio.model.source.NotesFilter
import dev.folio.model.workspace.AppDestination
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LiveSavedNotesTest {
    @Test fun geminiFiltersSavedNotesAndResumesExactAvailableAnnotationWithoutReadingPrivateDraft() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val file = File(context.cacheDir, "live-notes-${UUID.randomUUID()}.db")
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = ColdSourceFixture(database)
            val original = fixture.sampleState().let { it.copy(navigation = it.navigation.copy(destination = AppDestination.NOTES, notesQuery = "private local query")) }
            fixture.repository.save(original); fixture.session.attachWriter(scope); fixture.session.restore()
            val page = requireNotNull(fixture.warm.page(ArchitectureSources.DOCUMENT_ID, 0))
            fixture.pages.save(PageCacheKey(fixture.checksum, page.documentId, page.pageIndex, page.engineeringVersion), page)
            val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
                context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
            val provider = DirectGeminiMentor(profile, prompt)
            val assistant = AssistantSession(fixture.session, AppCapabilityContext(fixture.curriculum, sourceCatalog = fixture.catalog), fixture.library, fixture.catalog,
                provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                    val capability = request.capabilityContext.toString()
                    assertFalse("Private draft text leaked", capability.contains("Labelled unfinished private draft"))
                    assertFalse("Native search leaked", capability.contains("private local query"))
                    return provider.respond(request)
                } } }, effects = { assertTrue(it.isEmpty()) })
            suspend fun ask(text: String) {
                assistant.ask(text); assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
            }
            ask("Open the saved notes library and show Notes only. Do not edit any saved records.")
            assertEquals(AppDestination.NOTES, fixture.session.state.value.navigation.destination)
            assertEquals(NotesFilter.NOTES, fixture.session.state.value.navigation.notesFilter)
            ask("Open the saved source for my existing note whose text is Labelled retained note. Keep all notes and private drafts unchanged. Use its native saved annotation reference.")
            val state = fixture.session.state.value
            assertEquals(AppDestination.WORKSPACE, state.navigation.destination)
            val before = original.activities.getValue("reader").data as ActivityData.Reader
            val after = state.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(before.annotations, after.annotations); assertEquals(before.annotationDrafts, after.annotationDrafts)
            assertEquals(before.bookmarks, after.bookmarks); assertEquals(before.readingConfirmations, after.readingConfirmations)
            assertEquals(before.annotations.getValue("saved-note").source, state.context.source)
            assertEquals(original.activities.getValue("cached-quiz"), state.activities.getValue("cached-quiz"))
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); context.deleteDatabase(file.absolutePath) }
    }
}
