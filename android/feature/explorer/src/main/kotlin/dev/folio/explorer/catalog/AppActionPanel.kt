package dev.folio.explorer.catalog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.designsystem.primitive.FolioSurface
import dev.folio.runtime.action.AppActionRegistry
import dev.folio.ui.icon.IconRegistry

@Composable
internal fun AppActionPanel() {
    FolioSurface {
        Text("Semantic app actions · shared by touch and future AI")
        AppActionRegistry.all.forEach { action ->
            Text("${action.id}${if (!action.enabled) " (not connected)" else ""}: ${action.description}")
        }
        Text("Prebuilt icons: ${IconRegistry.all.values.joinToString()}")
    }
}
