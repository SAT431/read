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
import dev.folio.runtime.interaction.*
import dev.folio.runtime.practice.*
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.validation.WorkspaceValidator
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuizAttemptsStorageTest {
    @Test fun reopenedDatabaseKeepsSavedKeysConfidenceChecksCurrentDraftAndReadOnlySelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quiz-attempts-${UUID.randomUUID()}.db")
        val catalog = ArchitectureSources.catalog(); val registry = BuiltInComponents.registry()
        val source = SourceAnchor(ArchitectureSources.DOCUMENT_ID, 0, "cell-p2")
        val quiz = ActivityData.Quiz(listOf(Question("q", "Saved fixture question", listOf("A", "B", "C", "D"), 0, "Saved reference", listOf(source))),
            answers = mapOf("q" to 1), submitted = setOf("q"), confidence = mapOf("q" to AnswerConfidence(ConfidenceLevel.HIGH, 1, 10, true)),
            checks = mapOf("q" to QuizCheck(20, true, true)), selectionActors = mapOf("q" to CommandActor.AI))
        val created = CommandExecutor(registry, catalog).execute(ArchitectureActivities.initial(), CommandBatch(requestId = "fixture", commands = listOf(
            WorkspaceCommand.CreateActivity("quiz", "quiz-view", ComponentKind.QUIZ, source, quiz))), ExecutionPolicy(CommandActor.USER))
        assertTrue(created.trace.accepted)
        val history = QuizHistoryReducer(catalog) { 30L }
        val next = history.apply(created.state, LearningInteraction.RestartQuiz("quiz", 1), CommandActor.USER)
        val draft = LearningReducer(catalog).reduce(next, LearningInteraction.SelectAnswer("quiz", "q", 0))
        val state = history.apply(draft, LearningInteraction.ReviewQuizAttempt("quiz", 1, "q"), CommandActor.USER)
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
                WorkspaceValidator(registry, catalog).validate(restored); assertEquals(state, restored)
                val saved = restored.activities.getValue("quiz").data as ActivityData.Quiz
                assertEquals(mapOf("q" to 0), saved.answers); assertTrue(saved.submitted.isEmpty())
                assertEquals(mapOf("q" to 1), saved.pastAttempts.single().answers)
                assertEquals(QuizHistorySelection(1, "q"), saved.review)
                assertEquals(1, LearningMemory.summary(LearningMemory.observations(restored)).checked)
            } finally { second.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
