package dev.folio.runtime.assistant

import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.port.AssistantHistory

/** Never replay a command whose commit may already have happened. */
object AssistantHistoryRecovery {
    fun recover(history: AssistantHistory): AssistantHistory {
        val state = history.state
        return history.copy(state = when (state.status) {
            AssistantStatus.REQUESTING -> state.copy(status = AssistantStatus.FAILED, activeRequestId = null,
                retryUtterance = state.retryUtterance.takeIf { state.retrySafe },
                error = if (state.retrySafe) "The assistant request was interrupted. You can continue using fresh context." else
                    "The request was interrupted after app changes. Check current work and give a new request to continue.")
            AssistantStatus.PREPARING -> state.copy(status = AssistantStatus.CANCELLED, activeRequestId = null, retryUtterance = null,
                error = "Page preparation was interrupted. It may already be cached; check the current PDF and give a new request.")
            AssistantStatus.EXECUTING -> state.copy(status = AssistantStatus.CANCELLED, activeRequestId = null,
                retryUtterance = null, error = "An app action was interrupted and may have completed. Check your current page before asking again.")
            else -> state.copy(activeRequestId = null)
        })
    }
}
