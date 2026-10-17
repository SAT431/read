package dev.folio.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.folio.model.ui.ComponentKind
import dev.folio.runtime.registry.ComponentRegistry

/** Feature modules register renderers at the app composition root. Core UI knows no feature. */
class RendererRegistry(private val capabilities: ComponentRegistry, renderers: List<RegisteredRenderer>) {
    private val entries = renderers.associateBy { it.kind }
    init {
        require(entries.size == renderers.size) { "Duplicate renderer registrations" }
        require(entries.keys == capabilities.all().map { it.kind }.toSet()) { "Registry/renderer coverage mismatch" }
    }
    @Composable fun Render(context: ComponentRenderContext) {
        val renderer = entries[context.component.kind]
        if (renderer == null) Text("This component is unavailable") else renderer.Render(context)
    }
    fun kinds(): Set<ComponentKind> = entries.keys
}
