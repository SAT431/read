package dev.folio.model.command

import kotlinx.serialization.Serializable

@Serializable
data class CommandBatch(val schemaVersion: Int = 1, val requestId: String,
    val expectedRevision: Long? = null, val commands: List<WorkspaceCommand>)

enum class CommandActor { USER, AI }

data class ExecutionPolicy(val actor: CommandActor, val interactionLocked: Boolean = false,
    val userRequestedTransition: Boolean = false)
