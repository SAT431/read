package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.local.source.RoomPageRepository
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.*
import dev.folio.model.port.*
import dev.folio.model.source.EngineeringStatus
import dev.folio.remote.DirectGeminiMentor
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveAssistantPreparationTest {
    @Test fun oneLiveRequestPreparesOnlySelectedGeneratedPdfPageThenQuotesAndExplainsFreshSource() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val base = ApplicationProvider.getApplicationContext<Context>()
        val profile = requireNotNull(KeystoreProviderProfileStore(base).load())
        val fixture = PreparationDeviceFixture(base, CoroutineScope(SupervisorJob() + Dispatchers.Default), live = true)
        try {
            fixture.seed()
            val prompt = base.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
                base.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
            val mentor = DirectGeminiMentor(profile, prompt); var calls = 0; var freshSourceSeen = false
            val assistant = fixture.assistant(object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                calls++; assertFalse("Private preparation draft leaked", request.capabilityContext.toString().contains("Private preparation draft"))
                if (calls == 1) assertFalse(request.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean)
                else {
                    freshSourceSeen = request.sourceContext.getValue("sourcePageAvailable").jsonPrimitive.boolean
                    assertTrue(request.sourceContext.getValue("page").toString().contains("Amber", ignoreCase = true))
                }
                return mentor.respond(request)
            } })
            assistant.ask("Prepare my current original PDF page, then quote and explain its first body sentence in your reply automatically in this same request. Use the prepared source, stay on this PDF page, keep notes/private drafts unchanged, and do not process other pages or add study records.")
            assertEquals(assistant.state.value.error + " · fixture diagnostic " + fixture.failureSummary, AssistantStatus.COMPLETED, assistant.state.value.status)
            assertTrue("Fresh source was not used for a later model round", freshSourceSeen && calls in 2..3)
            assertEquals(1, fixture.engineCalls.get())
            val page = requireNotNull(fixture.pages.page(fixture.pdf.document.id, 0))
            assertEquals(EngineeringStatus.UNCHECKED, page.status); assertTrue(page.corrections.isEmpty())
            assertNull(fixture.pages.page(fixture.pdf.document.id, 1))
            val replies = assistant.state.value.messages.filter { it.role == MessageRole.ASSISTANT }.joinToString(" ") { it.text }
            assertTrue("Prepared sentence was not quoted/explained", replies.contains("amber", ignoreCase = true) && replies.contains("group", ignoreCase = true))
            val state = fixture.workspace.state.value
            assertEquals(fixture.original.activities, state.activities); assertEquals(fixture.original.navigation, state.navigation)
            assertArrayEquals(fixture.originalBytes, fixture.originalFile.readBytes())
            val recovered = RoomPageRepository(fixture.database.pages()); recovered.load()
            assertEquals(page, recovered.page(fixture.pdf.document.id, 0, fixture.checksum)); assertNotNull(recovered.fidelity(fixture.pdf.document.id, 0, fixture.checksum))
            assertTrue(java.io.File(fixture.context.cacheDir, "selected-pages").listFiles().orEmpty().isEmpty())
            assertEquals(1, assistant.state.value.messages.count { it.role == MessageRole.USER })
        } finally { fixture.close() }
    }
}
