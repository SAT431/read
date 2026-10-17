package dev.folio.runtime.command

import dev.folio.model.workspace.Workspace

data class CommandTrace(val requestId: String, val accepted: Boolean, val commands: List<String>, val error: String? = null)
data class ExecutionResult(val state: Workspace, val trace: CommandTrace)
