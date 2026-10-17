package com.example.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.snapshot.AtomicAssistantHistoryRepository
import dev.folio.model.assistant.*
import dev.folio.model.port.AssistantHistory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssistantHistoryDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun freshRepositoryRecoversRetainedVoiceCommandAndAtomicBackup() = runBlocking {
        val directory = File(context.cacheDir, "history-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val history = AssistantHistory(state = AssistantState(status = AssistantStatus.FAILED,
                messages = listOf(AssistantMessage("voice", MessageRole.USER, "Open Botany")),
                retryUtterance = "Open Botany", error = "Gemini HTTP 503"))
            AtomicAssistantHistoryRepository(directory).save(history)
            assertEquals(history, AtomicAssistantHistoryRepository(directory).load())
            val record = File(directory, "assistant-history-v1.json")
            assertTrue(record.renameTo(File(directory, "assistant-history-v1.json.bak")))
            assertEquals(history, AtomicAssistantHistoryRepository(directory).load())
        } finally { directory.deleteRecursively() }
    }
    @Test fun damagedRecordSurvivesRecoveryAndSaveAttempts() = runBlocking {
        val directory = File(context.cacheDir, "history-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val record = File(directory, "assistant-history-v1.json").apply { writeText("damaged-original") }
            val repository = AtomicAssistantHistoryRepository(directory)
            assertTrue(runCatching { repository.load() }.isFailure)
            assertTrue(runCatching { repository.save(AssistantHistory()) }.isFailure)
            assertEquals("damaged-original", record.readText())
        } finally { directory.deleteRecursively() }
    }
}
