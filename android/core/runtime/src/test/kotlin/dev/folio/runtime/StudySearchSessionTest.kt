package dev.folio.runtime

import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.workspace.*
import dev.folio.runtime.search.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class StudySearchSessionTest {
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = emptyList<Subject>()
        override fun chapters() = listOf(StudyChapter("cell", SubjectId.BOTANY, "Cell fixture", 1, 0))
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>()); override suspend fun load() {}
    }
    @Test fun nativeSessionRejectsStaleQueryAndSourceRevisionAndWaitsForBackgroundProjection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = MutableStateFlow(Workspace(navigation = AppNavigation(AppDestination.SEARCH, globalQuery = "cell")))
        val changes = MutableStateFlow(0L)
        val catalog = object : SourceCatalog by TestFixture.catalog { override val revision get() = changes.value }
        try {
            val session = StudySearchSession(scope, state, library, catalog, changes, curriculum)
            session.awaitCurrent(state.value); assertEquals(1, session.snapshotFor(state.value)!!.total)
            val next = state.value.copy(revision = 1, navigation = state.value.navigation.copy(globalQuery = "unknown"))
            assertNull(session.snapshotFor(next)); state.value = next
            session.awaitCurrent(next); assertEquals(0, session.snapshotFor(next)!!.total)
            library.entries.value = listOf(LibraryPdf("added", "cell", TestFixture.catalog.documents().first(), 1))
            withTimeout(3000) { while (session.snapshotFor(next) == null) delay(5) }
            assertEquals(library.entries.value, session.snapshotFor(next)!!.libraryEntries)
            changes.value = 1L
            withTimeout(3000) { while (session.snapshotFor(next) == null) delay(5) }
            assertEquals(1L, session.snapshotFor(next)!!.sourceRevision)
            state.value = next.copy(revision = 2, navigation = next.navigation.copy(destination = AppDestination.WORKSPACE))
            assertNull(session.snapshotFor(state.value))
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
    @Test fun newerQueryCancelsOldProjectionAndNeverPublishesItsResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>(); val release = java.util.concurrent.CountDownLatch(1)
        val catalog = object : SourceCatalog by TestFixture.catalog {
            override fun cachedPages(): List<dev.folio.model.source.SourcePage> {
                if (!started.isCompleted) { started.complete(Unit); release.await(2, java.util.concurrent.TimeUnit.SECONDS) }
                return emptyList()
            }
        }
        val state = MutableStateFlow(Workspace(navigation = AppNavigation(AppDestination.SEARCH, globalQuery = "cell")))
        try {
            val session = StudySearchSession(scope, state, library, catalog, MutableStateFlow(0L), curriculum)
            withTimeout(3000) { started.await() }
            state.value = state.value.copy(revision = 1, navigation = state.value.navigation.copy(globalQuery = "absent"))
            assertNull(session.snapshotFor(state.value)); release.countDown()
            session.awaitCurrent(state.value)
            val latest = session.snapshotFor(state.value)!!; assertEquals("absent", latest.query); assertEquals(0, latest.total)
        } finally { release.countDown(); scope.coroutineContext.job.cancelAndJoin() }
    }
}
