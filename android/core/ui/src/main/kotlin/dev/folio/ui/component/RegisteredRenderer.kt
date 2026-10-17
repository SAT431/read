package dev.folio.ui.component

import androidx.compose.runtime.Composable
import dev.folio.model.ui.ComponentKind

interface RegisteredRenderer {
    val kind: ComponentKind
    @Composable fun Render(context: ComponentRenderContext)
}
