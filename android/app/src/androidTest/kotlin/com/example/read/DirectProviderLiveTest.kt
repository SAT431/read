package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.port.*
import dev.folio.remote.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in only. Reads keys on device and never prints keys or provider response bodies. */
class DirectProviderLiveTest {
    @Test fun savedGeminiCanReturnAValidatedMentorEnvelope() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("folioLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = KeystoreProviderProfileStore(context).load()
        assertNotNull("Save API keys in Folio before running the live check", profile)
        assertEquals(GeminiModels.CURRENT_FLASH, profile!!.geminiModel)
        assertTrue("Groq key is not saved", profile.groqKey.isNotBlank())
        val prompt = context.assets.open("gemini-orchestrator.md").bufferedReader().use { it.readText() } +
            context.assets.open("native-tool-payloads.md").bufferedReader().use { it.readText() }
        val request = MentorRequest(requestId = "folio-direct-live-check",
            utterance = "This is a connection check. Reply briefly to say you are ready; no app action is requested, so batch must be null.",
            capabilityContext = buildJsonObject { put("schemaVersion", 1); put("revision", 0); put("actions", JsonArray(emptyList())) })
        try {
            val response = DirectGeminiMentor(profile, prompt).respond(request)
            assertEquals(request.requestId, response.requestId)
            assertNull("Connection check unexpectedly requested app actions", response.batch)
            assertTrue(response.message.isNotBlank())
        } catch (error: ProviderApiException) {
            fail("Live Gemini check failed with HTTP ${error.httpStatus}; retryAfterSeconds=${error.retryAfterSeconds}. No fallback was called.")
        }
    }
}
