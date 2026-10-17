package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSessionTest {
    @Test fun selectedAnswersWaitForTheDurableRepositoryWrite() = runBlocking {
        var saved: Workspace? = null
        val repository = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) { saved = workspace }
        }
        val initial = TestFixture.execute(TestFixture.reader(), TestFixture.quiz()).state
        val session = WorkspaceSession(initial, TestFixture.executor, repository,
            WorkspaceValidator(TestFixture.registry, TestFixture.catalog))
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        session.attachWriter(writerScope)
        session.interact(dev.folio.runtime.interaction.LearningInteraction.SelectAnswer("quiz", "q1", 2))
        assertEquals(2, (saved!!.activities.getValue("quiz").data as dev.folio.model.activity.ActivityData.Quiz).answers["q1"])
        writerScope.cancel()
    }
    @Test fun invalidRecoveryPreservesOriginalAndDisablesOverwrite() = runBlocking {
        var saved = false
        val repository = object : WorkspaceRepository {
            override suspend fun load() = TestFixture.reader().copy(schemaVersion = 99)
            override suspend fun save(workspace: Workspace) { saved = true }
        }
        val session = WorkspaceSession(TestFixture.reader(), TestFixture.executor, repository,
            WorkspaceValidator(TestFixture.registry, TestFixture.catalog))
        session.restore()
        session.dispatch(CommandBatch(requestId = "test", commands = listOf(TestFixture.quiz())), TestFixture.user)
        session.flush()
        assertFalse(saved); assertNotNull(session.storageError.value)
    }
}
