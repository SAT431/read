package dev.folio.model.port

import kotlinx.coroutines.flow.Flow

enum class VoiceStatus { IDLE, LISTENING, PROCESSING, SPEAKING, UNAVAILABLE }
data class VoiceEvent(val status: VoiceStatus, val transcript: String = "", val isFinal: Boolean = false)
interface VoiceGateway {
    val events: Flow<VoiceEvent>
    suspend fun startListening()
    suspend fun speak(text: String)
    fun stop()
}
