package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.read.fixture.ArchitectureSources
import dev.folio.local.database.*
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.local.source.PrivateSourceCacheBackup
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.remote.DirectGeminiMentor
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.source.SourceCacheRepairPhase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LiveSourceCacheRepairTest {
    @Test fun liveGeminiOpensPreviewWithoutApprovingOrChangingAnyCacheRows() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(context).load())
        val directory = File(context.cacheDir, "live-cache-repair-${UUID.randomUUID()}").apply { mkdirs() }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, File(directory, "fixture.db").absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val backups = File(directory, "backups")
            val fixture = ColdSourceFixture(database, PrivateSourceCacheBackup(backups))
            fixture.session.attachWriter(scope)
            val row = SourcePageRow(fixture.checksum, ArchitectureSources.DOCUMENT_ID, 0, 1, "bad-json-fixture", null)
            database.pages().save(row)
            val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
                context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
            val assistant = AssistantSession(fixture.session,
                AppCapabilityContext(fixture.curriculum, sourceCatalog = fixture.catalog, cacheRepairAvailable = true), fixture.library, fixture.catalog,
                provider = { DirectGeminiMentor(profile, prompt) }, effects = { effects -> effects.forEach { effect ->
                    assertEquals(AppEffect.CheckSourceCache, effect); fixture.cacheRepair.request()
                } })
            assistant.ask("Check my readable page cache and open the native repair preview. Do not approve repair or process any PDF page. I will decide after seeing the preview.")
            assertEquals(assistant.state.value.error, AssistantStatus.COMPLETED, assistant.state.value.status)
            assertEquals(SourceCacheRepairPhase.REVIEW, fixture.cacheRepair.state.value.phase)
            assertEquals(1, fixture.cacheRepair.state.value.preview!!.damagedPages)
            assertEquals(listOf(row), database.pages().readAll()); assertFalse(backups.exists())
            val token = fixture.cacheRepair.state.value.confirmationToken!!
            val rejected = fixture.session.dispatchApp(AppCommandBatch(requestId = "ai-native-boundary", commands = listOf(AppCommand.ConfirmSourceCacheRepair(token))), ExecutionPolicy(CommandActor.AI))
            assertFalse(rejected.trace.accepted); assertTrue(rejected.effects.isEmpty())
            assertEquals(listOf(row), database.pages().readAll()); fixture.cacheRepair.dismiss()
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); directory.deleteRecursively() }
    }
}
