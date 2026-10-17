package com.example.read

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.folio.designsystem.theme.FolioTheme
import dev.folio.model.command.AppCommand
import dev.folio.model.library.LibraryPdf
import dev.folio.model.source.SourceDocument
import dev.folio.model.study.*
import dev.folio.runtime.study.DocumentStudyRules
import dev.folio.study.document.StudySourcesSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated source picker: short content wraps; long content scrolls above a reachable action. */
class StudySourceSheetUiTest {
    @get:Rule val compose=createAndroidComposeRule<AssistantTestActivity>()
    @Test fun enlargedTextPickerUsesOnlyNeededSpaceAndKeepsLongListActionReachable() {
        val host=LibraryPdf("host","chapter",SourceDocument("host-doc","Controlled primary PDF",1,"host-hash",null),1)
        val other=LibraryPdf("other","chapter",SourceDocument("other-doc","Controlled second PDF",1,"other-hash",null),2)
        fun outline(count:Int)=DocumentOutline(other.document.id,other.document.checksum,1,listOf(0),
            (1..count).map{DocumentTopic("topic-$it","Controlled topic $it","Labelled fixture",0,0)})
        var map by mutableStateOf(outline(1))
        val commands=mutableListOf<AppCommand>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
                FolioTheme(false,reducedMotion=true) {
                    StudySourcesSheet(host,listOf(host,other),mapOf(other.document.id to map),emptyMap(),
                        DocumentStudyPlan(host.id,host.document.checksum,"a".repeat(64),listOf("base"),listOf(0)),{},commands::add)
                }
            }
        }
        compose.onNodeWithTag("harness-source-other").performClick()
        compose.onNodeWithTag("study-topic-topic-1").performClick()
        compose.waitForIdle()
        val small=compose.onNodeWithTag("study-source-sheet").fetchSemanticsNode().boundsInWindow
        assertTrue("Short picker unnecessarily fills the screen",small.height<compose.activity.window.decorView.height*.75f)
        compose.onNodeWithTag("harness-add-topics").assertIsDisplayed().assertIsEnabled()
        saveFixtureScreenshot("study-source-sheet-short.png")
        compose.runOnIdle{map=outline(60)}
        compose.onNodeWithTag("harness-source-topics").performScrollToNode(hasTestTag("study-topic-topic-60"))
        compose.onNodeWithTag("study-topic-topic-60").performClick()
        compose.onNodeWithTag("harness-add-topics").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(AppCommand.HarnessIncludeTopics(host.id,other.id,1,DocumentStudyRules.fingerprint(map),listOf("topic-60")),commands.last())
        compose.onNodeWithTag("study-sources-close").assertIsDisplayed()
        saveFixtureScreenshot("study-source-sheet-long.png")
    }
}
