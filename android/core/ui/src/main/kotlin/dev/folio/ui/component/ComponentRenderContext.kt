package dev.folio.ui.component

import dev.folio.model.activity.Activity
import dev.folio.model.command.WorkspaceCommand
import dev.folio.model.port.SourceCatalog
import dev.folio.model.port.MediaRepository
import dev.folio.model.ui.ComponentInstance
import dev.folio.model.workspace.UserPreferences
import dev.folio.runtime.interaction.LearningInteraction

data class ComponentRenderContext(val component: ComponentInstance, val activity: Activity,
    val catalog: SourceCatalog, val media: MediaRepository, val preferences: UserPreferences,
    val onCommands: (List<WorkspaceCommand>) -> Unit,
    val onInteraction: (LearningInteraction) -> Unit,
    val revisionItems: Map<String, dev.folio.model.activity.RevisionItem> = emptyMap())
