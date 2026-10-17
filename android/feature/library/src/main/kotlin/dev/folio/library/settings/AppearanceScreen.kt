package dev.folio.library.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.folio.model.command.AppCommand
import dev.folio.model.workspace.UserPreferences
import dev.folio.ui.control.SemanticButton

@Composable
fun AppearanceScreen(preferences: UserPreferences, dispatch: (AppCommand) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make room for your mind.", style = MaterialTheme.typography.headlineLarge)
        SemanticButton("theme-toggle", if (preferences.darkTheme) "Use light theme" else "Use dark theme",
            AppCommand.SetPreferences(preferences.copy(darkTheme = !preferences.darkTheme)), dispatch)
        SemanticButton("motion-toggle", if (preferences.reducedMotion) "Enable animations" else "Reduce motion",
            AppCommand.SetPreferences(preferences.copy(reducedMotion = !preferences.reducedMotion)), dispatch)
        Text("Reading size: ${preferences.readingSizeSp} sp")
        SemanticButton("font-increase", "Larger reading text", AppCommand.SetPreferences(preferences.copy(readingSizeSp = (preferences.readingSizeSp + 2).coerceAtMost(36))), dispatch)
        SemanticButton("font-decrease", "Smaller reading text", AppCommand.SetPreferences(preferences.copy(readingSizeSp = (preferences.readingSizeSp - 2).coerceAtLeast(14))), dispatch)
        Text("Tap Speak, talk, then pause. Speech sends automatically; Stop is optional.")
        HorizontalDivider()
        Text("Readable page storage", style = MaterialTheme.typography.titleMedium)
        Text("Check cached pages if a saved passage cannot load. Folio previews any repair and makes a private backup before changes.")
        SemanticButton("source-cache-check", "Check readable page cache", AppCommand.CheckSourceCache, dispatch)
        Text("Add your Gemini and Groq API keys in Assistant settings. No server or hosting is required.")
    }
}
