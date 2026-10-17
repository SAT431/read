package com.example.read

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.*
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.practice.PracticeTimerCoordinator
import dev.folio.runtime.reading.SentenceIndex
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Two explicit harness stages separated by Android process termination. Only cache fixture files are used. */
class ProcessRecoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun runId(): String {
        val value = InstrumentationRegistry.getArguments().getString("folioRecoveryRun")
        assumeTrue("Requires explicit process-recovery harness", value != null)
        return requireNotNull(value).also { require(it.matches(Regex("[a-z0-9-]{1,48}"))) }
    }
    private fun databaseFile(run: String) = File(context.cacheDir, "folio-process-$run.db")
    private fun markerFile(run: String) = File(context.cacheDir, "folio-process-$run.json")
    private val legacy = object : WorkspaceRepository {
        override suspend fun load(): Workspace? = null
        override suspend fun save(workspace: Workspace) {}
    }

    @Test fun seedDurableWorkForProcessTermination() = runBlocking {
        val run = runId(); val file = databaseFile(run); val marker = markerFile(run)
        require(!file.exists() && !marker.exists()) { "Use a new recovery run identifier" }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomWorkspaceRepository(database.workspace(), legacy)
            val fixture = PracticeTimerFixture(SystemClock::elapsedRealtime, repository)
            fixture.workspace.attachWriter(scope)
            val sentence = SentenceIndex().compile(requireNotNull(fixture.catalog.page("fixture-cell", 0))).first()
            fixture.act(AppCommand.AddReadingBookmark("reader", "process-bookmark", sentence.source, sentence.id))
            fixture.act(AppCommand.ConfirmReading("reader", sentence.source, expectedReadingRevision = 0))
            fixture.act(AppCommand.CreateAnnotation("reader", "process-note", sentence.source, sentence.id, AnnotationKind.NOTE, "Labelled saved note"))
            fixture.act(AppCommand.SaveAnnotationDraft("reader", "process-draft", AnnotationDraft(sentence.source, text = "Labelled unfinished private draft")))
            fixture.act(AppCommand.RestartQuiz("timer-quiz", 1))
            fixture.act(AppCommand.SelectAnswer("timer-quiz", "timer-q", 0))
            fixture.act(AppCommand.SetAnswerConfidence("timer-quiz", "timer-q", 0, null, ConfidenceLevel.MEDIUM))
            fixture.act(AppCommand.SetPracticeTimerLimit("timer-quiz", 2, 0, 30))
            fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 2, 1))
            delay(1200); fixture.workspace.flush()
            val saved = requireNotNull(repository.load())
            assertEquals(fixture.workspace.state.value, saved)
            assertEquals(TimerStatus.RUNNING, fixture.data().timer.status)
            assertTrue(fixture.data().timer.elapsedMillis >= 1000)
            marker.writeText(JSONObject().put("seedPid", Process.myPid()).put("seedElapsedRealtime", SystemClock.elapsedRealtime())
                .put("snapshot", WorkspaceSnapshotCodec().encode(saved)).toString(), Charsets.UTF_8)
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close() }
    }

    @Test fun verifyFreshProcessRestoresOnlyDurableCheckpointAndAllLearningState() = runBlocking {
        val run = runId(); val file = databaseFile(run); val marker = markerFile(run)
        require(file.exists() && marker.exists()) { "Run the seed stage first" }
        val metadata = JSONObject(marker.readText(Charsets.UTF_8))
        assertNotEquals("Verification must run in a new Android process", metadata.getInt("seedPid"), Process.myPid())
        assertTrue(SystemClock.elapsedRealtime() - metadata.getLong("seedElapsedRealtime") >= 1000)
        val expected = WorkspaceSnapshotCodec().decode(metadata.getString("snapshot"))
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var verified = false
        try {
            val repository = RoomWorkspaceRepository(database.workspace(), legacy)
            val fixture = PracticeTimerFixture(SystemClock::elapsedRealtime, repository)
            fixture.workspace.attachWriter(scope); fixture.workspace.restore()
            assertNull(fixture.workspace.storageError.value)
            val recovered = PracticeTimerCoordinator { error("Recovery must not count away time") }.recover(expected)
            assertEquals(recovered, fixture.workspace.state.value)
            assertEquals(recovered, repository.load())
            val before = (expected.activities.getValue("timer-quiz").data as ActivityData.Quiz).timer.elapsedMillis
            assertEquals(before, fixture.data().timer.elapsedMillis)
            assertEquals(TimerStatus.PAUSED, fixture.data().timer.status)
            assertEquals(TimerChange.RECOVERED, fixture.data().timer.lastChange)
            assertEquals(mapOf("timer-q" to 0), fixture.data().answers)
            assertTrue(fixture.data().submitted.isEmpty())
            assertEquals(ConfidenceLevel.MEDIUM, fixture.data().confidence.getValue("timer-q").level)
            assertEquals(mapOf("timer-q" to 1), fixture.data().pastAttempts.single().answers)
            val reader = fixture.workspace.state.value.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(1, reader.bookmarks.size); assertEquals(1, reader.readingConfirmations.size)
            assertEquals("Labelled saved note", reader.annotations.getValue("process-note").text)
            assertEquals("Labelled unfinished private draft", reader.annotationDrafts.getValue("process-draft").text)
            fixture.workspace.setForeground(true); fixture.workspace.tickTimers()
            assertEquals(before, fixture.data().timer.elapsedMillis)
            fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 2, fixture.data().timer.version))
            delay(250); fixture.workspace.tickTimers()
            assertTrue(fixture.data().timer.elapsedMillis > before)
            verified = true
        } finally {
            scope.coroutineContext.job.cancelAndJoin(); database.close()
            if (verified) { context.deleteDatabase(file.absolutePath); assertTrue(marker.delete()) }
        }
    }
}
