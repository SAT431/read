package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.read.fixture.ArchitectureSources
import com.example.read.navigation.RecoveredSourceNotice
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.local.database.*
import dev.folio.model.activity.*
import dev.folio.model.command.*
import dev.folio.model.port.MediaRepository
import dev.folio.runtime.action.LearningCommandBridge
import dev.folio.runtime.state.WorkspaceStartupRecovery
import dev.folio.study.quiz.QuizRenderer
import dev.folio.study.reader.ReaderRenderer
import dev.folio.ui.component.ComponentRenderContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ColdSourceRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun corruptSourceCacheDoesNotHideSavedWorkOrAllowUnavailableSourceReview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val file = File(context.cacheDir, "cold-source-${UUID.randomUUID()}.db")
        val first = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val seeded = ColdSourceFixture(first)
        val original = seeded.sampleState()
        try {
            seeded.repository.save(original)
            first.pages().save(SourcePageRow(seeded.checksum, ArchitectureSources.DOCUMENT_ID, 0, 1, "bad-json-fixture", null))
        } finally { first.close() }
        val second = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val recovered = ColdSourceFixture(second); recovered.session.attachWriter(scope)
            val warnings = WorkspaceStartupRecovery().recover(recovered.library::load, recovered.pages::load, recovered.session::restore)
            assertEquals(1, warnings.size); assertNull(recovered.session.storageError.value)
            assertEquals(original, recovered.session.state.value)
            assertTrue(recovered.session.recoveredUnavailableSources.value.isNotEmpty())
            var readerVisible by mutableStateOf(false)
            compose.setContent { FolioTheme(false) {
                val state by recovered.session.state.collectAsState()
                val missing by recovered.session.recoveredUnavailableSources.collectAsState()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("cold-source-root")) {
                    Text("Isolated cache-recovery fixture", style = MaterialTheme.typography.labelSmall)
                    RecoveredSourceNotice(state, missing, recovered.catalog, 0)
                    TextButton({ runBlocking { recovered.act(AppCommand.WorkspaceAction(WorkspaceCommand.ResumeActivity("reader"))) }; readerVisible = true }) {
                        Text("Fixture reader records")
                    }
                    val id = if (readerVisible) "reader" else "cached-quiz"
                    val view = if (readerVisible) "reader-view" else "cached-quiz-view"
                    val render = ComponentRenderContext(state.components.getValue(view), state.activities.getValue(id), recovered.catalog,
                        object : MediaRepository { override suspend fun read(mediaId: String): ByteArray? = null }, state.preferences,
                        { commands -> runBlocking { commands.forEach { recovered.act(AppCommand.WorkspaceAction(it)) } } },
                        { runBlocking { recovered.act(LearningCommandBridge.command(it)) } })
                    if (readerVisible) ReaderRenderer().Render(render) else QuizRenderer().Render(render)
                }
            } }
            compose.onNodeWithTag("source-recovery-notice").assertExists()
            compose.onNodeWithText("Next question").performScrollTo().performClick()
            compose.onNodeWithText("Labelled cached question two").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("quiz-source-cached-q2-0").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Fixture reader records").performScrollTo().performClick()
            compose.onNodeWithTag("reading-checkpoints-show-reader").performScrollTo().performClick()
            val reader = recovered.session.state.value.activities.getValue("reader").data as ActivityData.Reader
            val report = reader.readingConfirmations.values.single()
            compose.onNodeWithTag("reading-checkpoint-open-${report.id}").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("cold-source-root").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10000f) }
            compose.waitForIdle(); saveFixtureScreenshot("cold-source-recovery.png")
            compose.onNodeWithTag("reading-checkpoint-clear-${report.id}").performScrollTo().performClick()
            compose.runOnIdle {
                val data = recovered.session.state.value.activities.getValue("reader").data as ActivityData.Reader
                assertTrue(data.readingConfirmations.isEmpty()); assertEquals(1, data.bookmarks.size)
                assertEquals("Labelled retained note", data.annotations.getValue("saved-note").text)
                assertEquals("Labelled unfinished private draft", data.annotationDrafts.getValue("saved-draft").text)
                val quiz = recovered.session.state.value.activities.getValue("cached-quiz").data as ActivityData.Quiz
                assertEquals(mapOf("cached-q1" to 1), quiz.answers); assertEquals(setOf("cached-q1"), quiz.submitted)
                assertEquals(ConfidenceLevel.HIGH, quiz.confidence.getValue("cached-q1").level)
            }
            recovered.session.flush()
            assertEquals(recovered.session.state.value, recovered.repository.load())
            assertEquals("bad-json-fixture", second.pages().readAll().single().payload)
        } finally { scope.coroutineContext.job.cancelAndJoin(); second.close(); context.deleteDatabase(file.absolutePath) }
    }
}
