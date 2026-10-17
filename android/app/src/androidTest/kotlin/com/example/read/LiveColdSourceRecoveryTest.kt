package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.ArchitectureSources
import dev.folio.local.database.*
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.state.WorkspaceStartupRecovery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LiveColdSourceRecoveryTest {
    @Test fun geminiCanNavigateCachedQuestionsWhileOriginalSourceRemainsUnavailable() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>(); val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val file = File(context.cacheDir, "live-cold-source-${UUID.randomUUID()}.db")
        val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val seeded = ColdSourceFixture(first); val original = seeded.sampleState()
        try {
            seeded.repository.save(original)
            first.pages().save(SourcePageRow(seeded.checksum, ArchitectureSources.DOCUMENT_ID, 0, 1, "bad-json-fixture", null))
        } finally { first.close() }
        val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = ColdSourceFixture(second); fixture.session.attachWriter(scope)
            assertEquals(1, WorkspaceStartupRecovery().recover(fixture.library::load, fixture.pages::load, fixture.session::restore).size)
            assertNull(fixture.session.storageError.value)
            // Preserve the private draft, and hide its reader before asking for a quiz transition.
            fixture.act(AppCommand.WorkspaceAction(WorkspaceCommand.SuspendActivity("reader")))
            val gate = InteractionGate(); assertFalse(gate.blocks(fixture.session.state.value))
            val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
                context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
            val assistant = AssistantSession(fixture.session, AppCapabilityContext(fixture.curriculum, sourceCatalog = fixture.catalog), fixture.library, fixture.catalog,
                provider = { DirectGeminiMentor(profile, prompt) }, effects = { assertTrue(it.isEmpty()) },
                interactionLocked = { gate.blocks(fixture.session.state.value) })
            assistant.ask("Open question 2 of my existing saved quiz. Use only its cached question snapshot. Keep all answers and notes, and do not open or reconstruct the unavailable original source.")
            assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
            val state = fixture.session.state.value; val quiz = state.activities.getValue("cached-quiz").data as ActivityData.Quiz
            assertEquals(1, quiz.index); assertEquals(mapOf("cached-q1" to 1), quiz.answers); assertEquals(setOf("cached-q1"), quiz.submitted)
            assertEquals(original.activities.keys, state.activities.keys)
            assertFalse(fixture.catalog.contains(quiz.questions[1].sources.single()))
            val reader = state.activities.getValue("reader").data as ActivityData.Reader
            assertEquals("Labelled unfinished private draft", reader.annotationDrafts.getValue("saved-draft").text)
            assertEquals("bad-json-fixture", second.pages().readAll().single().payload)
        } finally { scope.coroutineContext.job.cancelAndJoin(); second.close(); context.deleteDatabase(file.absolutePath) }
    }
}
