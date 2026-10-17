package dev.folio.runtime

import dev.folio.model.assistant.*
import dev.folio.model.port.AssistantHistory
import dev.folio.runtime.assistant.AssistantHistoryRecovery
import org.junit.Assert.*
import org.junit.Test

class AssistantHistoryRecoveryTest {
    @Test fun interruptedRequestRetainsRetryWithoutPretendingItIsStillRunning() {
        val before = AssistantHistory(state = AssistantState(status = AssistantStatus.REQUESTING,
            activeRequestId = "r1", retryUtterance = "Open Botany"))
        val state = AssistantHistoryRecovery.recover(before).state
        assertEquals(AssistantStatus.FAILED, state.status)
        assertEquals("Open Botany", state.retryUtterance)
        assertNull(state.activeRequestId)
        assertFalse(state.busy)
    }
    @Test fun interruptedCommitNeverOffersReplay() {
        val before = AssistantHistory(state = AssistantState(status = AssistantStatus.EXECUTING,
            activeRequestId = "r1", retryUtterance = "Next page"))
        val state = AssistantHistoryRecovery.recover(before).state
        assertEquals(AssistantStatus.CANCELLED, state.status)
        assertNull(state.retryUtterance)
        assertNull(state.activeRequestId)
        assertTrue(state.error!!.contains("may have completed"))
    }
    @org.junit.Test fun interruptedContinuationRetainsAcceptanceButUnsafeProgressCannotReplay() {
        val history = dev.folio.model.port.AssistantHistory(state = dev.folio.model.assistant.AssistantState(
            status = dev.folio.model.assistant.AssistantStatus.REQUESTING, round = 2, retryUtterance = "Continue fixture",
            acceptedActions = listOf("quiz.restart"), retrySafe = false))
        val recovered = dev.folio.runtime.assistant.AssistantHistoryRecovery.recover(history)
        org.junit.Assert.assertNull(recovered.state.retryUtterance)
        org.junit.Assert.assertEquals(listOf("quiz.restart"), recovered.state.acceptedActions)
        org.junit.Assert.assertEquals(dev.folio.model.assistant.AssistantStatus.FAILED, recovered.state.status)
    }
    @org.junit.Test fun interruptedPagePreparationNeverRestoresABusySpinnerOrReplaysProviderWork() {
        val history = dev.folio.model.port.AssistantHistory(state = dev.folio.model.assistant.AssistantState(
            status = dev.folio.model.assistant.AssistantStatus.PREPARING, retryUtterance = "Prepare and explain",
            acceptedActions = listOf("source.engineer_page"), retrySafe = false))
        val recovered = dev.folio.runtime.assistant.AssistantHistoryRecovery.recover(history)
        org.junit.Assert.assertEquals(dev.folio.model.assistant.AssistantStatus.CANCELLED, recovered.state.status)
        org.junit.Assert.assertFalse(recovered.state.busy); org.junit.Assert.assertNull(recovered.state.retryUtterance)
        org.junit.Assert.assertEquals(history.state.acceptedActions, recovered.state.acceptedActions)
    }
}
