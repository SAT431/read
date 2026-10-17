package com.example.read

import dev.folio.local.database.FolioDatabase
import dev.folio.local.snapshot.AtomicAssistantHistoryRepository
import dev.folio.model.assistant.AssistantStatus
import dev.folio.model.command.*
import dev.folio.model.port.*
import dev.folio.model.search.*
import dev.folio.model.workspace.AppDestination
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import dev.folio.runtime.interaction.InteractionGate
import dev.folio.runtime.search.StudySearchSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*

internal class ContinuationDeviceFixture(database: FolioDatabase, directory: File, scope: CoroutineScope) {
    val source = ColdSourceFixture(database)
    val history = AtomicAssistantHistoryRepository(directory)
    val original = source.sampleState().let { it.copy(navigation = it.navigation.copy(destination = AppDestination.SEARCH)) }
    val search = StudySearchSession(scope, source.session.state, source.library, source.catalog, source.pages.revision, source.curriculum)
    private val gate = InteractionGate()
    init { source.session.attachWriter(scope) }
    suspend fun seed() { source.repository.save(original); source.session.restore(); assertNull(source.session.storageError.value) }
    fun assistant(provider: MentorProvider) = AssistantSession(source.session,
        AppCapabilityContext(source.curriculum, sourceCatalog = source.catalog, searchSnapshot = search::snapshotFor), source.library, source.catalog,
        provider = { provider }, effects = { assertTrue(it.isEmpty()) }, history = history,
        interactionLocked = { gate.blocks(source.session.state.value) }, readyForContinuation = search::awaitCurrent)
    fun searchResponse(request: MentorRequest) = response(request, AppCommand.SearchStudy("Labelled cached question two", SearchKind.QUIZ), true)
    fun openResponse(request: MentorRequest): MentorResponse {
        val result = Json.decodeFromJsonElement<SearchResult>(request.capabilityContext.getValue("search").jsonObject.getValue("results").jsonArray.single())
        return response(request, AppCommand.OpenSearchResult(result.target, result.fingerprint), false)
    }
    private fun response(request: MentorRequest, command: AppCommand, more: Boolean) = MentorResponse(requestId = request.requestId,
        message = "Labelled native continuation fixture step", batch = AppCommandBatch(requestId = request.requestId,
            expectedRevision = request.capabilityContext.getValue("revision").jsonPrimitive.long, commands = listOf(command)), continueAfterExecution = more)
}
