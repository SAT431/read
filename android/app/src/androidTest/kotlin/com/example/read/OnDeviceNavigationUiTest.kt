package com.example.read

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.folio.assistant.AssistantStatusBar
import dev.folio.catalog.Neet2026Catalog
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.library.pdf.OriginalPdfScreen
import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.local.media.LocalMediaRepository
import dev.folio.local.pdf.NativePdfTools
import dev.folio.model.assistant.*
import dev.folio.model.command.*
import dev.folio.model.library.*
import dev.folio.model.port.*
import dev.folio.model.source.SourceDocument
import dev.folio.model.workspace.*
import dev.folio.runtime.action.*
import dev.folio.runtime.assistant.*
import dev.folio.runtime.command.CommandExecutor
import dev.folio.runtime.registry.BuiltInComponents
import dev.folio.runtime.state.WorkspaceSession
import dev.folio.runtime.validation.WorkspaceValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Transcript handoff fixture, not a microphone/Groq or live Gemini acceptance test. */
class OnDeviceNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun transcriptsOpenSubjectOriginalPdfAndNextPageWithoutProviderThenTutorReceivesUpdatedContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "on-device-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = File(File(root, "pdfs").apply { mkdirs() }, "sample.pdf")
            val pdfFile = PdfDocument()
            try {
                repeat(3) { index ->
                    val page = pdfFile.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                    page.canvas.drawColor(if (index == 1) Color.LTGRAY else Color.WHITE)
                    pdfFile.finishPage(page)
                }
                file.outputStream().use(pdfFile::writeTo)
            } finally { pdfFile.close() }
            val curriculum = Neet2026Catalog()
            val chapter = curriculum.chapters().first { it.subject == SubjectId.BOTANY }
            val document = SourceDocument("native-shortcut", "Shortcut Sample.pdf", 3, "fixture-checksum", file.path)
            val pdf = LibraryPdf("shortcut-pdf", chapter.id, document, 0)
            val catalog = InMemorySourceCatalog(listOf(document), emptyList(), emptyList())
            val library = object : PdfLibrary {
                override val entries = MutableStateFlow(listOf(pdf))
                override suspend fun load() {}
            }
            val registry = BuiltInComponents.registry()
            val commands = CommandExecutor(registry, catalog)
            val workspace = WorkspaceSession(Workspace(), commands, object : WorkspaceRepository {
                override suspend fun load(): Workspace? = null
                override suspend fun save(workspace: Workspace) {}
            }, WorkspaceValidator(registry, catalog), appExecutor = AppCommandExecutor(commands, curriculum, library, catalog))
            var providerCalls = 0
            val assistant = AssistantSession(workspace, AppCapabilityContext(curriculum), library, catalog, provider = {
                object : MentorProvider { override suspend fun respond(request: MentorRequest): MentorResponse {
                    providerCalls++
                    assertEquals(1, request.sourceContext.getValue("activeSource").jsonObject.getValue("pageIndex").jsonPrimitive.int)
                    assertEquals(4, request.executionResults.size)
                    assertTrue(request.executionResults.all { it.getValue("executionOrigin").jsonPrimitive.content == "on_device" })
                    return MentorResponse(requestId = request.requestId, message = "Controlled tutor fixture", batch = null)
                } }
            }, effects = {}, localNavigation = LocalNavigationCommands(curriculum))
            val tools = NativePdfTools(root, catalog)
            val media = LocalMediaRepository(File(root, "source-assets"))
            compose.setContent { FolioTheme(false) {
                val state by workspace.state.collectAsState()
                val conversation by assistant.state.collectAsState()
                val scope = rememberCoroutineScope()
                fun transcript(text: String) {
                    val router = VoiceTranscriptRouter { value -> scope.launch { assistant.ask(value) }; true }
                    assertEquals("", router.received(text, automaticAtStart = true, automaticNow = true).transcript)
                }
                Column(Modifier.fillMaxSize()) {
                    FlowRow {
                        listOf("Open Botany", "Open newtons laws of motion chapter", "Open Shortcut Sample.pdf", "Go to the next page", "Explain this page").forEach { text ->
                            TextButton({ transcript(text) }) { Text("Fixture: $text") }
                        }
                    }
                    AssistantStatusBar(conversation, {}, {})
                    if (state.navigation.destination == AppDestination.PDF) Box(Modifier.weight(1f)) {
                        OriginalPdfScreen(pdf, state.navigation, tools, media) { action -> scope.launch {
                            workspace.dispatchApp(AppCommandBatch(requestId = "touch", commands = listOf(action)), ExecutionPolicy(CommandActor.USER))
                        } }
                    }
                }
            } }
            compose.onNodeWithText("Fixture: Open Botany").performClick()
            compose.waitUntil(10000) { workspace.state.value.navigation.subject == SubjectId.BOTANY && assistant.state.value.status == AssistantStatus.COMPLETED }
            compose.onNodeWithText("Fixture: Open newtons laws of motion chapter").performClick()
            val motionChapter = curriculum.chapters().single { it.subject == SubjectId.PHYSICS && it.title == "Laws of Motion" }
            compose.waitUntil(10000) { workspace.state.value.navigation.chapterId == motionChapter.id && assistant.state.value.status == AssistantStatus.COMPLETED }
            compose.onNodeWithText("Fixture: Open Shortcut Sample.pdf").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Original PDF page 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture: Go to the next page").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Original PDF page 2").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                assertEquals(0, providerCalls)
                assertEquals(1, workspace.state.value.navigation.pdfPageIndex)
                assertEquals(4, assistant.state.value.messages.count { it.role == MessageRole.SYSTEM && it.text.startsWith("On-device navigation:") })
            }
            compose.onNodeWithText("Fixture: Explain this page").performClick()
            compose.waitUntil(10000) { providerCalls == 1 && assistant.state.value.status == AssistantStatus.COMPLETED }
            compose.runOnIdle { assertEquals(1, workspace.state.value.navigation.pdfPageIndex) }
        } finally { root.deleteRecursively() }
    }
}
