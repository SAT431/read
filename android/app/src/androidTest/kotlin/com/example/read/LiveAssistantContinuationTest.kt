package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.database.FolioDatabase
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.*
import dev.folio.model.workspace.AppDestination
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.search.StudySearchSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LiveAssistantContinuationTest {
    @Test fun oneGeminiRequestContinuesFromNativeSearchIntoExactCachedQuestionWithoutAnotherSend() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>(); val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val file = File(context.cacheDir, "live-continuation-${UUID.randomUUID()}.db")
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = ColdSourceFixture(database)
            val original = fixture.sampleState().let { it.copy(navigation = it.navigation.copy(destination = AppDestination.SEARCH)) }
            fixture.repository.save(original); fixture.session.attachWriter(scope); fixture.session.restore()
            val search = StudySearchSession(scope, fixture.session.state, fixture.library, fixture.catalog, fixture.pages.revision, fixture.curriculum)
            val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
                context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
            val provider = DirectGeminiMentor(profile, prompt); val gate = InteractionGate()
            var calls = 0
            val assistant = AssistantSession(fixture.session, AppCapabilityContext(fixture.curriculum, sourceCatalog = fixture.catalog, searchSnapshot = search::snapshotFor),
                fixture.library, fixture.catalog, provider = { object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                    calls++
                    assertFalse("Private draft leaked into search context", request.capabilityContext.toString().contains("Labelled unfinished private draft"))
                    return provider.respond(request)
                } } }, effects = { assertTrue(it.isEmpty()) }, interactionLocked = { gate.blocks(fixture.session.state.value) }, readyForContinuation = search::awaitCurrent)
            assistant.ask("Search my saved quiz questions for exactly Labelled cached question two and open its one native search result automatically in this same request. Use fresh search results, keep all learning work and private drafts unchanged, and do not create or process anything.")
            assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
            assertTrue("Compound request did not use fresh provider rounds", calls in 2..3)
            assertTrue(assistant.state.value.acceptedActions.containsAll(listOf("search.query", "search.open")))
            assertEquals(1, assistant.state.value.messages.count { it.role == dev.folio.model.assistant.MessageRole.USER })
            val state = fixture.session.state.value; assertEquals(AppDestination.WORKSPACE, state.navigation.destination)
            val quiz = state.activities.getValue("cached-quiz").data as ActivityData.Quiz
            assertEquals(1, quiz.index); assertEquals(mapOf("cached-q1" to 1), quiz.answers); assertEquals(setOf("cached-q1"), quiz.submitted)
            val before = original.activities.getValue("reader").data as ActivityData.Reader
            val after = state.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(before.annotations, after.annotations); assertEquals(before.annotationDrafts, after.annotationDrafts)
            assertEquals(before.bookmarks, after.bookmarks); assertEquals(before.readingConfirmations, after.readingConfirmations)
            assertEquals(original.activities.keys, state.activities.keys)
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); context.deleteDatabase(file.absolutePath) }
    }
}
