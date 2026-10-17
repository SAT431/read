package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AssistantContinuationStorageTest {
    @Test fun freshRoomAndAtomicHistoryResumeOnlyTheRemainingSafeStep() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "continuation-storage-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var retained: dev.folio.model.workspace.Workspace? = null
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, File(directory, "fixture.db").absolutePath).build()
            try {
                val fixture = ContinuationDeviceFixture(first, directory, scope); fixture.seed(); var calls = 0
                val assistant = fixture.assistant(object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                    return if (calls++ == 0) fixture.searchResponse(request) else throw ProviderApiException(AiProvider.GEMINI, 503)
                } })
                assistant.ask("Find and open the labelled cached question")
                assertEquals(AssistantStatus.FAILED, assistant.state.value.status); assertEquals(listOf("search.query"), assistant.state.value.acceptedActions)
                assertNotNull(assistant.state.value.retryUtterance); fixture.source.session.flush(); retained = fixture.source.session.state.value
            } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, File(directory, "fixture.db").absolutePath).build()
            try {
                val fixture = ContinuationDeviceFixture(second, directory, scope); fixture.source.session.restore()
                assertEquals(retained, fixture.source.session.state.value); var calls = 0
                val assistant = fixture.assistant(object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                    calls++; assertTrue(request.sourceContext.getValue("continuation").jsonObject.getValue("resumeAfterFailure").jsonPrimitive.boolean)
                    assertEquals(1L, request.capabilityContext.getValue("revision").jsonPrimitive.long - fixture.original.revision)
                    return fixture.openResponse(request)
                } })
                assistant.restore(); assertEquals(0, calls); assistant.retry()
                assertEquals(1, calls); assertEquals(AssistantStatus.COMPLETED, assistant.state.value.status)
                val state = fixture.source.session.state.value; val quiz = state.activities.getValue("cached-quiz").data as ActivityData.Quiz
                assertEquals(1, quiz.index); assertEquals(mapOf("cached-q1" to 1), quiz.answers)
                assertEquals((retained!!.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts,
                    (state.activities.getValue("reader").data as ActivityData.Reader).annotationDrafts)
                assertEquals(listOf("search.query", "search.open"), assistant.state.value.acceptedActions)
                assertEquals(assistant.state.value, fixture.history.load()!!.state)
            } finally { second.close() }
        } finally { scope.coroutineContext.job.cancelAndJoin(); directory.deleteRecursively() }
    }
}
