package dev.folio.model.port

import dev.folio.model.workspace.Workspace

interface WorkspaceRepository {
    suspend fun load(): Workspace?
    suspend fun save(workspace: Workspace)
}
