package dev.folio.runtime

import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.library.*
import dev.folio.runtime.action.*
import dev.folio.runtime.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SourceCacheRepairSessionTest {
    private class Store : SourceCacheRepairStore {
        var repairs = 0; var healthy = false; var fail = false
        override suspend fun previewRepair() = SourceCacheRepairPreview("fixture", 2, if (healthy) 0 else 1, 0)
        override suspend fun repair(expectedFingerprint: String): SourceCacheRepairReceipt {
            if (fail) error("Never expose private payload")
            repairs++; return SourceCacheRepairReceipt("private-fixture", 1, 0, 1)
        }
    }
    private val curriculum = object : CurriculumCatalog {
        override val baseline = "fixture"; override val sourceUrl = "fixture"
        override fun subjects() = emptyList<Subject>(); override fun chapters() = emptyList<StudyChapter>()
    }
    private val library = object : PdfLibrary {
        override val entries = MutableStateFlow(emptyList<LibraryPdf>()); override suspend fun load() {}
    }
    @Test fun requestAndCancelNeverRepairAndNewSessionCannotReplayApproval() = runBlocking {
        val store = Store(); val session = SourceCacheRepairSession(store)
        session.request(); val old = session.state.value.confirmationToken!!
        assertEquals(0, store.repairs); session.dismiss(); session.confirm(old)
        assertEquals(0, store.repairs); session.request()
        assertNotEquals(old, session.state.value.confirmationToken)
        assertFalse(SourceCacheRepairSession(store).canConfirm(old))
    }
    @Test fun nativeTokenIsOneUseAndProviderCannotConfirmEvenWithToken() = runBlocking {
        val store = Store(); val session = SourceCacheRepairSession(store); session.request()
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, cacheRepair = session)
        val token = session.state.value.confirmationToken!!
        val batch = AppCommandBatch(requestId = "fixture", commands = listOf(AppCommand.ConfirmSourceCacheRepair(token)))
        assertFalse(executor.execute(TestFixture.reader(), batch, TestFixture.ai).trace.accepted)
        val accepted = executor.execute(TestFixture.reader(), batch, TestFixture.user)
        assertTrue(accepted.trace.accepted); assertTrue(session.canConfirm(token)); assertEquals(0, store.repairs)
        session.confirm((accepted.effects.single() as AppEffect.ConfirmSourceCacheRepair).token)
        session.confirm(token); assertEquals(1, store.repairs); assertFalse(session.canConfirm(token))
    }
    @Test fun invalidAtomicBatchDoesNotConsumeApprovalOrEmitRepair() = runBlocking {
        val store = Store(); val session = SourceCacheRepairSession(store); session.request()
        val token = session.state.value.confirmationToken!!
        val executor = AppCommandExecutor(TestFixture.executor, curriculum, library, cacheRepair = session)
        val result = executor.execute(TestFixture.reader(), AppCommandBatch(requestId = "fixture", commands = listOf(
            AppCommand.ConfirmSourceCacheRepair(token), AppCommand.OpenChapterLibrary("missing"))), TestFixture.user)
        assertFalse(result.trace.accepted); assertTrue(result.effects.isEmpty()); assertTrue(session.canConfirm(token)); assertEquals(0, store.repairs)
    }
    @Test fun failureConsumesTokenAndDoesNotExposePrivateError() = runBlocking {
        val store = Store().apply { fail = true }; val session = SourceCacheRepairSession(store); session.request()
        val token = session.state.value.confirmationToken!!; session.confirm(token)
        assertEquals(SourceCacheRepairPhase.ERROR, session.state.value.phase); assertFalse(session.canConfirm(token))
        assertFalse(session.state.value.message!!.contains("private payload")); assertNull(session.state.value.confirmationToken)
    }
    @Test fun healthyCheckHasNoApprovalAndProviderContextExcludesNativeConfirm() = runBlocking {
        val store = Store().apply { healthy = true }; val session = SourceCacheRepairSession(store); session.request()
        assertEquals(SourceCacheRepairPhase.DONE, session.state.value.phase); assertNull(session.state.value.confirmationToken)
        val context = Json.parseToJsonElement(AppCapabilityContext(curriculum, cacheRepairAvailable = true).encode(TestFixture.reader(), emptyList())).jsonObject
        val actions = context.getValue("actions").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }
        assertFalse(actions.getValue("source.cache.confirm_repair").getValue("enabled").jsonPrimitive.boolean)
        assertTrue(actions.getValue("source.cache.check").getValue("enabled").jsonPrimitive.boolean)
        val result = AppCommandExecutor(TestFixture.executor, curriculum, library, cacheRepair = session).execute(TestFixture.reader(),
            AppCommandBatch(requestId = "fixture", commands = listOf(AppCommand.CheckSourceCache)), TestFixture.ai)
        assertEquals(listOf(AppEffect.CheckSourceCache), result.effects); assertEquals(0, store.repairs)
    }
}
