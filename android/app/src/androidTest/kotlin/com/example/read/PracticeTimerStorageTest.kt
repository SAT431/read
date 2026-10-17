package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.model.activity.*
import dev.folio.model.command.AppCommand
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PracticeTimerStorageTest {
    @Test fun roomReopenRestoresPausedCheckpointAndArchivedAttemptTime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "practice-timer-${UUID.randomUUID()}.db")
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var now = 100L
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val fixture = PracticeTimerFixture({ now }, RoomWorkspaceRepository(first.workspace(), legacy))
                fixture.workspace.attachWriter(scope)
                fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 1, 0))
                now += 900
                fixture.act(AppCommand.RestartQuiz("timer-quiz", 1))
                fixture.act(AppCommand.StartPracticeTimer("timer-quiz", 2, 0))
                now += 600; fixture.workspace.flush()
            } finally { first.close() }
            now += 50000
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val repository = RoomWorkspaceRepository(second.workspace(), legacy)
                val restored = PracticeTimerFixture({ now }, repository)
                restored.workspace.attachWriter(scope); restored.workspace.restore()
                val data = restored.data()
                assertEquals(600L, data.timer.elapsedMillis)
                assertEquals(TimerChange.RECOVERED, data.timer.lastChange)
                assertEquals(TimerStatus.PAUSED, data.timer.status)
                assertEquals(900L, data.pastAttempts.single().timer?.elapsedMillis)
                assertEquals(TimerChange.ARCHIVED, data.pastAttempts.single().timer?.lastChange)
                assertTrue(data.answers.isEmpty() && data.submitted.isEmpty())
                assertEquals(restored.workspace.state.value, repository.load())
            } finally { second.close() }
        } finally { scope.cancel(); context.deleteDatabase(file.absolutePath) }
    }
}
