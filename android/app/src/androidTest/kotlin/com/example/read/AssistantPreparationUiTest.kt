package com.example.read

import android.content.Context
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
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.model.activity.ActivityData
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.EngineeringRequest
import dev.folio.model.workspace.AppDestination
import dev.folio.runtime.action.LearningCommandBridge
import dev.folio.runtime.layout.LayoutSolver
import dev.folio.study.reader.PageEngineeringControls
import dev.folio.study.registry.StudyRenderers
import dev.folio.ui.component.RendererRegistry
import dev.folio.ui.media.LocalSourceRevision
import dev.folio.workspace.WorkspaceScreen
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantPreparationUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun singleRequestJoinsPageControlJobAtLargeTextAndExplainsOnlyAfterCacheReady() = runBlocking { checkFlow(0) }
    @Test fun cancelStopsThePlanButSharedPageCanFinishWithoutOpeningAnExplanation() = runBlocking { checkFlow(1) }
    @Test fun selectingNextPdfPageStopsOldPlanBeforeItsJobCompletes() = runBlocking { checkFlow(2) }
    private suspend fun checkFlow(mode: Int) {
        val fixture = PreparationDeviceFixture(ApplicationProvider.getApplicationContext<Context>(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        try {
            fixture.seed()
            val manual = EngineeringRequest("native-page-control", fixture.pdf.document.id, 0, fixture.checksum, dev.folio.model.port.PageEngineeringVersion.CURRENT)
            val pageJob = fixture.jobs.start(manual)
            val assistant = fixture.assistant(fixture.fakeProvider())
            val request = fixture.scope.launch { assistant.ask("Prepare the selected page and explain it") }
            val renderers = RendererRegistry(fixture.registry, StudyRenderers.all()); val solver = LayoutSolver(fixture.registry)
            compose.setContent { FolioTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                    val state by fixture.workspace.state.collectAsState()
                    val jobs by fixture.jobs.jobs.collectAsState()
                    val status by assistant.state.collectAsState()
                    val revision by fixture.pages.revision.collectAsState()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Text("Isolated native page-preparation fixture")
                        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalSourceRevision provides revision) {
                            if (state.navigation.destination == AppDestination.PDF) Column {
                                val page = state.navigation.pdfPageIndex
                                PageEngineeringControls(fixture.pdf.document.id, page, fixture.catalog.page(fixture.pdf.document.id, page) != null,
                                    jobs[dev.folio.runtime.engineering.PagePreparationJobs.key(fixture.pdf.document.id, page)]?.busy == true,
                                    jobs[dev.folio.runtime.engineering.PagePreparationJobs.key(fixture.pdf.document.id, page)]?.message, null) { runBlocking { fixture.act(it) } }
                                OriginalPdfScreen(fixture.pdf, state.navigation, fixture.tools, fixture.media) { runBlocking { fixture.act(it) } }
                            } else WorkspaceScreen(state, fixture.catalog, fixture.media, fixture.registry, renderers, solver,
                                { commands -> runBlocking { commands.forEach { fixture.act(dev.folio.model.command.AppCommand.WorkspaceAction(it)) } } },
                                { runBlocking { fixture.act(LearningCommandBridge.command(it)) } }, {})
                        } }
                        AssistantStatusBar(status, { request.cancel() }, {})
                    }
                }
            } }
            withTimeout(10000) { fixture.started.await(); while (assistant.state.value.status != AssistantStatus.PREPARING) delay(5) }
            compose.onNodeWithText("Assistant: preparing", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            assertEquals(1, fixture.engineCalls.get()); assertEquals(1, fixture.mentorCalls.get())
            when (mode) {
                1 -> compose.onNodeWithText("Cancel").performClick()
                2 -> compose.onNodeWithTag("pdf-next").assertIsEnabled().performClick()
                else -> { compose.waitForIdle(); Thread.sleep(500); saveFixtureScreenshot("assistant-preparing-large-text.png") }
            }
            if (mode != 0) withTimeout(10000) { request.join() }
            fixture.release.complete(Unit); withTimeout(10000) { pageJob.await(); request.join() }
            val state = fixture.workspace.state.value
            assertEquals(if (mode == 0) AssistantStatus.COMPLETED else if (mode == 1) AssistantStatus.CANCELLED else AssistantStatus.REJECTED, assistant.state.value.status)
            assertEquals(if (mode == 0) 2 else 1, fixture.mentorCalls.get()); assertEquals(1, fixture.engineCalls.get())
            assertEquals(mode == 0, "prepared-explanation" in state.activities)
            if (mode == 2) assertEquals(1, state.navigation.pdfPageIndex)
            val before = fixture.original.activities.getValue("reader").data as ActivityData.Reader
            val after = state.activities.getValue("reader").data as ActivityData.Reader
            assertEquals(before.annotations, after.annotations); assertEquals(before.annotationDrafts, after.annotationDrafts)
            assertArrayEquals(fixture.originalBytes, fixture.originalFile.readBytes())
            assertNotNull(fixture.pages.page(fixture.pdf.document.id, 0)); fixture.workspace.flush(); assertEquals(state, fixture.repository.load())
        } finally { fixture.close() }
    }
}
