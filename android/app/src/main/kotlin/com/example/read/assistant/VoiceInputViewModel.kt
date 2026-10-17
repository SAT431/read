package com.example.read.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.read.di.AppGraph
import dev.folio.local.audio.MicrophoneRecorder
import dev.folio.remote.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.folio.model.assistant.VoiceInputState
import dev.folio.runtime.assistant.VoiceTranscriptRouter
import dev.folio.runtime.assistant.EndOfSpeechDetector
import dev.folio.runtime.assistant.SpeechEndpoint

class VoiceInputViewModel(private val graph: AppGraph, private val recorder: MicrophoneRecorder,
    private val submit: (String) -> Boolean) : ViewModel() {
    private val mutableState = graph.voiceInputState
    private var job: Job? = null
    private var endpointJob: Job? = null
    private val mutableReviewRequest = MutableStateFlow(0L)
    val reviewRequest = mutableReviewRequest.asStateFlow()
    private var automaticAtStart = false
    private var guidedAtStart: dev.folio.runtime.study.GuidedVoiceTarget? = null
    private val transcripts = VoiceTranscriptRouter(submit)
    val state = mutableState.asStateFlow()
    fun start() {
        if (state.value.recording || state.value.processing) return
        try {
            automaticAtStart = graph.session.state.value.preferences.voiceAutomaticSend
            guidedAtStart = dev.folio.runtime.study.GuidedStudyVoice.target(graph.session.state.value)
            recorder.start(::finish); mutableState.value = VoiceInputState(recording = true)
            endpointJob = viewModelScope.launch {
                val detector = EndOfSpeechDetector()
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    while (state.value.recording) {
                        delay(100)
                        when (detector.observe(android.os.SystemClock.elapsedRealtime() - started, recorder.amplitude())) {
                            SpeechEndpoint.FINISHED, SpeechEndpoint.TIME_LIMIT -> { finish(); return@launch }
                            SpeechEndpoint.NO_SPEECH -> {
                                recorder.cancel(); mutableState.value = VoiceInputState(error = "No speech detected. Tap Speak and try again."); return@launch
                            }
                            SpeechEndpoint.LISTENING -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { recorder.cancel(); mutableState.value = VoiceInputState(error = "Microphone interrupted. Tap Speak to try again.") }
            }
        }
        catch (_: Exception) { mutableState.value = VoiceInputState(error = "Microphone could not start. Check its permission and availability.") }
    }
    fun finish() {
        if (!state.value.recording) return
        endpointJob?.cancel(); endpointJob = null
        val audio = recorder.finish()
        if (audio == null) { mutableState.value = VoiceInputState(error = "Recording was too short or unavailable. Try again."); return }
        mutableState.value = VoiceInputState(processing = true)
        job = viewModelScope.launch {
            try {
                val profile = graph.providerProfiles.load() ?: error("API keys not configured")
                val transcript = DirectGroqTranscriber(profile).transcribe(audio)
                ensureActive()
                if (!dev.folio.runtime.study.GuidedStudyVoice.maySubmit(transcript.text, guidedAtStart, graph.session.state.value)) {
                    mutableState.value = VoiceInputState(error = "The highlighted line changed while you were speaking. Confirm the new line with a fresh Speak request.")
                    return@launch
                }
                mutableState.value = transcripts.received(transcript.text, automaticAtStart,
                    graph.session.state.value.preferences.voiceAutomaticSend)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: dev.folio.model.port.ProviderApiException) { mutableState.value = VoiceInputState(error = error.message) }
            catch (error: dev.folio.model.port.ProviderFailureException) { mutableState.value = VoiceInputState(error = error.message) }
            catch (_: Exception) { mutableState.value = VoiceInputState(error = "Transcription failed. Check the Groq API key, connection and quota.") }
            finally { File(audio.path).delete() }
        }
    }
    fun edit(text: String) { if (text.length <= 4000) mutableState.value = state.value.copy(transcript = text) }
    fun send() {
        mutableState.value = transcripts.send(state.value)
    }
    fun permissionDenied() { mutableState.value = VoiceInputState(error = "Microphone permission was not granted. Text input is available.") }
    fun review() { mutableReviewRequest.value++ }
    fun cancel() { endpointJob?.cancel(); recorder.cancel(); job?.cancel(); mutableState.value = VoiceInputState() }
    override fun onCleared() { recorder.cancel(); super.onCleared() }
}
