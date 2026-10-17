package com.example.read

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*

/** Call only while an isolated labelled fixture is visible. Never capture credentials or learner PDFs. */
internal fun saveFixtureScreenshot(name: String) {
    require(name.matches(Regex("[a-z-]+\\.png")))
    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    assertNotNull(screenshot)
    try {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally { screenshot.recycle() }
}
