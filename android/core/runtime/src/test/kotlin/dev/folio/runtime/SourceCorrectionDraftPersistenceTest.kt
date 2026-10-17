package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.source.*
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.action.AppCommandExecutor
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SourceCorrectionDraftPersistenceTest {
    @Test fun discardWaitsForRepositoryWriteBeforeDispatchCompletes() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var saved: Workspace? = null
        val repository = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) { entered.complete(Unit); release.await(); saved = workspace }
        }
        val curriculum = object : CurriculumCatalog {
            override val baseline = "test"; override val sourceUrl = "https://test.example"
            override fun subjects() = emptyList<dev.folio.model.library.Subject>()
            override fun chapters() = emptyList<dev.folio.model.library.StudyChapter>()
        }
        val library = object : PdfLibrary {
            override val entries = MutableStateFlow(emptyList<dev.folio.model.library.LibraryPdf>())
            override suspend fun load() {}
        }
        val draft = SourceCorrectionDraft(TestFixture.source, "hash", "Source.", "Unfinished")
        val initial = Workspace(correctionDrafts = mapOf("draft" to draft))
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, TestFixture.catalog)
        val session = WorkspaceSession(initial, TestFixture.executor, repository,
            WorkspaceValidator(TestFixture.registry, TestFixture.catalog), appExecutor = executor)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        session.attachWriter(scope)
        try {
            val discard = async { session.dispatchApp(AppCommandBatch(requestId = "discard", commands = listOf(
                AppCommand.SaveCorrectionDraft("draft", null))), TestFixture.user) }
            withTimeout(5000) { entered.await() }
            assertFalse(discard.isCompleted)
            release.complete(Unit)
            assertTrue(withTimeout(5000) { discard.await() }.trace.accepted)
            assertTrue(requireNotNull(saved).correctionDrafts.isEmpty())
        } finally { release.complete(Unit); scope.cancel() }
    }
}
