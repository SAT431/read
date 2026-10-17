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
import dev.folio.model.workspace.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.practice.LearningMemory
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.validation.WorkspaceValidator
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConfidenceStorageTest {
    @Test fun roomReopenRetainsLockedConfidenceSourceActorAndMemoryFilter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "confidence-${UUID.randomUUID()}.db")
        val catalog = ArchitectureSources.catalog(); val registry = BuiltInComponents.registry()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val quiz = ActivityData.Quiz(listOf(Question("q", "Fixture confidence question", listOf("A", "B", "C", "D"), 0, "Reference", listOf(source))),
            answers = mapOf("q" to 1), submitted = setOf("q"), confidence = mapOf("q" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 123L, true)))
        val created = CommandExecutor(registry, catalog).execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.error, created.trace.accepted)
        val state = created.state.copy(navigation = AppNavigation(AppDestination.LEARNING, learningFilter = LearningFilter.CONFIDENT_MISTAKES))
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
                assertEquals(1, LearningMemory.summary(LearningMemory.observations(restored)).confidentMistakes)
                assertTrue((restored.activities.getValue("quiz").data as ActivityData.Quiz).confidence.getValue("q").byAssistant)
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
