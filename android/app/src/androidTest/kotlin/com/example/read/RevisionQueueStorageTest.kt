package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.read.fixture.*
import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.RoomWorkspaceRepository
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.WorkspaceRepository
import dev.folio.model.source.SourceAnchor
import dev.folio.model.ui.ComponentKind
import dev.folio.model.workspace.Workspace
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.practice.RevisionQueueReducer
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.validation.WorkspaceValidator
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RevisionQueueStorageTest {
    @Test fun reopenedRoomQueueRetainsDueTimesCompletionProvenanceAndPrivateActivityAnswer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "revision-${UUID.randomUUID()}.db")
        val catalog = ArchitectureSources.catalog()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val registry = BuiltInComponents.registry()
        val initial = CommandExecutor(registry, catalog).execute(ArchitectureActivities.initial(), CommandBatch(requestId = "create", commands = listOf(
            WorkspaceCommand.CreateActivity("recall", "recall-view", ComponentKind.RECALL, source,
                ActivityData.Recall("Prompt", "Reference", listOf(source), response = "Private retained answer", revealed = true, rating = RecallRating.PARTIAL, ratedAtEpochMillis = 123L, ratingByAssistant = true)))), ExecutionPolicy(CommandActor.USER))
        assertTrue(initial.trace.accepted)
        val reducer = RevisionQueueReducer(catalog) { 123456L }
        val scheduled = reducer.apply(initial.state, AppCommand.ScheduleRevision("recall", null, 0), CommandActor.AI)
        val item = scheduled.revisionItems.values.single()
        val completed = reducer.apply(scheduled, AppCommand.CompleteRevision(item.id, item.version), CommandActor.USER)
        val advice = dev.folio.runtime.practice.RevisionSuggestions.forActivity(completed.activities.getValue("recall"), null)!!
        val state = reducer.apply(completed, AppCommand.AcceptRevisionSuggestion("recall", null, advice.evidenceFingerprint), CommandActor.USER)
        val legacy = object : WorkspaceRepository {
            override suspend fun load(): Workspace? = null
            override suspend fun save(workspace: Workspace) {}
        }
        try {
            val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try { RoomWorkspaceRepository(first.workspace(), legacy).save(state) } finally { first.close() }
            val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
            try {
                val restored = requireNotNull(RoomWorkspaceRepository(second.workspace(), legacy).load())
                WorkspaceValidator(registry, catalog).validate(restored)
                assertEquals(state, restored)
                assertEquals(1, restored.revisionItems.values.single().completions.size)
                assertFalse(restored.revisionItems.values.single().done)
                assertEquals(advice, restored.revisionItems.values.single().suggestion)
                assertEquals("Prompt", restored.revisionItems.values.single().completions.single().prompt)
                assertEquals(source, restored.revisionItems.values.single().completions.single().source)
                assertEquals("Private retained answer", (restored.activities.getValue("recall").data as ActivityData.Recall).response)
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
