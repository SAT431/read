package com.example.read.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.read.di.AppGraph
import dev.folio.model.port.ProviderProfile
import dev.folio.model.port.GeminiModels
import dev.folio.model.command.AppEffect
import dev.folio.remote.*
import dev.folio.runtime.action.AppCapabilityContext
import dev.folio.runtime.assistant.AssistantSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class AssistantViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutableModel = MutableStateFlow<String?>(null)
    private val mutableGroq = MutableStateFlow(false)
    private val mutableSaving = MutableStateFlow(true)
    private val mutableError = MutableStateFlow<String?>(null)
    private val effectChannel = Channel<AppEffect>(Channel.BUFFERED)
    private var request: Job? = null
    val savedModel = mutableModel.asStateFlow()
    val groqConfigured = mutableGroq.asStateFlow()
    val saving = mutableSaving.asStateFlow()
    val connectionError = mutableError.asStateFlow()
    val effects = effectChannel.receiveAsFlow()
    private val session = AssistantSession(graph.session, AppCapabilityContext(graph.curriculum, sourceCatalog = graph.catalog, cacheRepairAvailable = true,
        searchSnapshot = graph.search::snapshotFor, documentStudyAvailable = true), graph.library, graph.catalog,
        provider = {
            val profile = graph.providerProfiles.load() ?: error("Configure Gemini")
            DirectGeminiMentor(profile, graph.mentorPrompt)
        }, effects = { it.forEach { effect -> effectChannel.send(effect) } }, engineeringAvailable = true,
        liveContext = { buildJsonObject {
            put("documentStudy", dev.folio.runtime.study.StudyAssistantContext.build(graph.session.state.value, graph.library, graph.documentOutlines))
            put("voice", Json { encodeDefaults = true }.encodeToJsonElement(graph.voiceInputState.value))
            put("interactionLocked", graph.interactionLocked)
            put("sourceCacheRepair", buildJsonObject {
                val repair = graph.cacheRepair.state.value
                put("phase", repair.phase.name)
                repair.preview?.let { put("damagedPages", it.damagedPages); put("damagedReports", it.damagedReports) }
                put("nativeConfirmationRequired", true)
            })
        } },
        history = graph.assistantHistory, interactionLocked = { graph.interactionLocked },
        localNavigation = dev.folio.runtime.assistant.LocalNavigationCommands(graph.curriculum), readyForContinuation = graph.search::awaitCurrent, effectAwaiter = graph.preparationAwaiter)
    val state = session.state
    init { viewModelScope.launch {
        try { graph.providerProfiles.load()?.let { mutableModel.value = it.geminiModel; mutableGroq.value = it.groqKey.isNotBlank() } }
        catch (_: Exception) { mutableError.value = "Saved API keys could not be recovered. Re-enter them or remove saved keys." }
        finally { session.restore(); mutableSaving.value = false }
    } }
    fun save(geminiKey: String, groqKey: String, model: String) {
        if (mutableSaving.value || state.value.busy) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                val previous = graph.providerProfiles.load()
                val profile = ProviderProfile(geminiKey.ifBlank { previous?.geminiKey.orEmpty() },
                    groqKey.ifBlank { previous?.groqKey.orEmpty() }, GeminiModels.CURRENT_FLASH)
                graph.providerProfiles.save(profile)
                mutableModel.value = profile.geminiModel; mutableGroq.value = profile.groqKey.isNotBlank(); mutableError.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = "API keys could not be saved. Check the Gemini key and device secure storage." }
            finally { mutableSaving.value = false }
        }
    }
    fun disconnect() {
        if (mutableSaving.value || state.value.busy) return
        mutableSaving.value = true
        viewModelScope.launch {
            try { graph.providerProfiles.clear(); mutableModel.value = null; mutableGroq.value = false; mutableError.value = null }
            catch (_: Exception) { mutableError.value = "Saved API keys could not be removed." }
            finally { mutableSaving.value = false }
        }
    }
    fun ask(text: String): Boolean {
        if (request?.isActive == true || mutableSaving.value || mutableModel.value == null || text.isBlank() || text.length > 4000) return false
        request = viewModelScope.launch { session.ask(text) }
        return true
    }
    fun cancel() { if (state.value.status != dev.folio.model.assistant.AssistantStatus.EXECUTING) request?.cancel() }
    fun retry() {
        if (request?.isActive == true || mutableSaving.value || mutableModel.value == null) return
        request = viewModelScope.launch { session.retry() }
    }
}
