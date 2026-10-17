package com.example.read

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.folio.model.port.MediaRepository
import dev.folio.ui.media.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class SourceImageUiTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantTestActivity>()
    @Test fun validImagesDecodeAndPreviousPixelsDisappearDuringAnotherLoad() {
        fun png(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            return try {
                bitmap.eraseColor(color)
                ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
            } finally { bitmap.recycle() }
        }
        val red = png(Color.RED); val blue = png(Color.BLUE)
        val releaseSecond = CompletableDeferred<Unit>()
        val id = mutableStateOf("first")
        val media = object : MediaRepository {
            override suspend fun read(mediaId: String): ByteArray = when (mediaId) {
                "first" -> red
                "second" -> { releaseSecond.await(); blue }
                else -> byteArrayOf(1, 2, 3)
            }
        }
        compose.setContent {
            Column {
                TextButton({ id.value = "second" }) { Text("Load second") }
                TextButton({ id.value = "invalid" }) { Text("Load invalid") }
                when (val image = rememberSourceImageState(id.value, media)) {
                    SourceImageState.Loading -> Text("Image loading")
                    is SourceImageState.Unavailable -> Text("Image unavailable")
                    is SourceImageState.Ready -> Text("pixel=${image.bitmap.getPixel(0, 0)}")
                }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("pixel=${Color.RED}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Load second").performClick()
        compose.onNodeWithText("Image loading").assertExists()
        compose.onNodeWithText("pixel=${Color.RED}").assertDoesNotExist()
        compose.runOnIdle { releaseSecond.complete(Unit) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("pixel=${Color.BLUE}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Load invalid").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Image unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("pixel=${Color.BLUE}").assertDoesNotExist()
    }
}
