package com.example.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.assistant.AssistantStatusBar
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.search.StudySearchScreen
import dev.folio.local.database.FolioDatabase
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.*
import dev.folio.model.workspace.AppDestination
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AssistantContinuationUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun singleSendShowsFreshSecondStepAtLargeTextThenOpensWithoutAnotherSend() = runBlocking { checkFlow(false) }
    @Test fun cancellingSecondStepKeepsFirstSearchAndDoesNotOpenDelayedTarget() = runBlocking { checkFlow(true) }
    private suspend fun checkFlow(cancel: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "continuation-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, File(directory, "fixture.db").absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val fixture = ContinuationDeviceFixture(database, directory, scope); fixture.seed()
            val second = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val assistant = fixture.assistant(object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                return if (request.sourceContext.getValue("continuation").jsonObject.getValue("round").jsonPrimitive.int == 1)
                    fixture.searchResponse(request) else { second.complete(Unit); release.await(); fixture.openResponse(request) }
            } })
            val job = scope.launch { assistant.ask("Find the labelled cached question and open it in one request") }
            compose.setContent { FolioTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                    val workspace by fixture.source.session.state.collectAsState()
                    val status by assistant.state.collectAsState()
                    val search by fixture.search.state.collectAsState()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Text("Isolated bounded assistant fixture")
                        Box(Modifier.weight(1f)) {
                            if (workspace.navigation.destination == AppDestination.SEARCH)
                                StudySearchScreen(workspace, search.takeIf { fixture.search.snapshotFor(workspace) == it }) { runBlocking { fixture.source.act(it) } }
                            else Text("Labelled compound target opened")
                        }
                        AssistantStatusBar(status, { job.cancel() }, {})
                    }
                }
            } }
            withTimeout(10000) { second.await() }
            compose.onNodeWithText("step 2 of 3", substring = true).assertIsDisplayed()
            if (cancel) compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
            else { compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("assistant-continuation-large-text.png"); release.complete(Unit) }
            withTimeout(10000) { job.join() }
            val state = fixture.source.session.state.value
            val quiz = state.activities.getValue("cached-quiz").data as ActivityData.Quiz
            assertEquals(if (cancel) AssistantStatus.CANCELLED else AssistantStatus.COMPLETED, assistant.state.value.status)
            assertEquals(if (cancel) 0 else 1, quiz.index); assertEquals(mapOf("cached-q1" to 1), quiz.answers)
            val before = fixture.original.activities.getValue("reader").data as ActivityData.Reader
            val after = state.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(before.annotationDrafts, after.annotationDrafts); assertEquals(before.annotations, after.annotations)
            assertEquals(before.bookmarks, after.bookmarks); assertEquals(before.readingConfirmations, after.readingConfirmations)
            assertEquals(1, assistant.state.value.messages.count { it.role == dev.folio.model.assistant.MessageRole.USER })
            assertEquals(assistant.state.value, fixture.history.load()!!.state)
            fixture.source.session.flush(); assertEquals(state, fixture.source.repository.load())
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); directory.deleteRecursively() }
    }
}
