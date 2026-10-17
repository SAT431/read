package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.read.fixture.ArchitectureSources
import dev.folio.local.database.FolioDatabase
import dev.folio.model.command.AppCommand
import dev.folio.model.port.PageCacheKey
import dev.folio.model.search.SearchKind
import dev.folio.runtime.search.StudySearchSession
import dev.folio.runtime.state.WorkspaceStartupRecovery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StudySearchStorageTest {
    @Test fun freshRoomSessionRestoresSearchQueryFilterPageAndIndexesDurableCachedPassageWithoutProvider() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val file = File(context.cacheDir, "search-storage-${UUID.randomUUID()}.db")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var expected: dev.folio.model.workspace.Workspace? = null
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val fixture = ColdSourceFixture(first); fixture.repository.save(fixture.sampleState()); fixture.session.attachWriter(scope); fixture.session.restore()
                val page = requireNotNull(fixture.warm.page(ArchitectureSources.DOCUMENT_ID, 0)).let { it.copy(isFixture = false,
                    elements = it.elements.map { e -> if (e.id == "cell-p1") e.copy(originalText = "Labelled durable searchable passage.") else e }) }
                fixture.pages.save(PageCacheKey(fixture.checksum, page.documentId, 0, page.engineeringVersion), page)
                fixture.act(AppCommand.SearchStudy("Labelled durable searchable", SearchKind.PASSAGE)); fixture.act(AppCommand.SearchPage(0))
                fixture.session.flush(); expected = fixture.session.state.value
            } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val fixture = ColdSourceFixture(second); fixture.session.attachWriter(scope)
                assertTrue(WorkspaceStartupRecovery().recover(fixture.library::load, fixture.pages::load, fixture.session::restore).isEmpty())
                assertEquals(expected, fixture.session.state.value)
                val search = StudySearchSession(scope, fixture.session.state, fixture.library, fixture.catalog, fixture.pages.revision, fixture.curriculum)
                search.awaitCurrent(fixture.session.state.value)
                val result = search.snapshotFor(fixture.session.state.value)!!.results.single()
                assertEquals("Labelled durable searchable passage.", result.excerpt); assertEquals(SearchKind.PASSAGE, result.target.kind)
                assertEquals(0, result.target.pageIndex); assertTrue(result.canOpen)
                assertEquals(expected, fixture.repository.load())
            } finally { second.close() }
        } finally { scope.coroutineContext.job.cancelAndJoin(); context.deleteDatabase(file.absolutePath) }
    }
}
