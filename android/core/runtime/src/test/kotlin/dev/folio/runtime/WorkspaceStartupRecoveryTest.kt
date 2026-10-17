package dev.folio.runtime

import dev.folio.runtime.state.WorkspaceStartupRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceStartupRecoveryTest {
    @Test fun eachFailedSourceStageStillAttemptsTheWorkspaceAndDoesNotExposeExceptionContent() = runBlocking {
        val stages = mutableListOf<String>()
        val warnings = WorkspaceStartupRecovery().recover(
            { stages.add("library"); error("private sample path") },
            { stages.add("pages"); error("private sample cached words") },
            { stages.add("workspace") })
        assertEquals(listOf("library", "pages", "workspace"), stages)
        assertEquals(2, warnings.size); assertFalse(warnings.any { it.contains("private sample") })
    }
    @Test fun cancellationDoesNotProceedToLaterRecoveryStages() = runBlocking {
        val stages = mutableListOf<String>()
        try {
            WorkspaceStartupRecovery().recover({ stages.add("library") },
                { throw CancellationException("Controlled cancellation") }, { stages.add("workspace") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(listOf("library"), stages) }
    }
    @Test fun successfulRecoveryHasNoWarningsAndUnexpectedWorkspaceFailurePropagates() = runBlocking {
        assertTrue(WorkspaceStartupRecovery().recover({}, {}, {}).isEmpty())
        assertTrue(runCatching { WorkspaceStartupRecovery().recover({}, {}, { error("Controlled workspace failure") }) }.isFailure)
    }
}
