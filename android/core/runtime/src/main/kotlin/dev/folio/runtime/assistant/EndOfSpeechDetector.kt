package dev.folio.runtime.assistant

enum class SpeechEndpoint { LISTENING, FINISHED, NO_SPEECH, TIME_LIMIT }

/** Local energy endpointing: speech must be sustained, then followed by a quiet pause. */
class EndOfSpeechDetector {
    private var previousMs = 0L
    private var speechMs = 0L
    private var lastSpeechMs: Long? = null
    private var floor = 150.0
    private var result = SpeechEndpoint.LISTENING
    fun observe(elapsedMs: Long, amplitude: Int): SpeechEndpoint {
        require(elapsedMs >= previousMs && amplitude in 0..32767)
        if (result != SpeechEndpoint.LISTENING) return result
        val interval = (elapsedMs - previousMs).coerceAtMost(200)
        previousMs = elapsedMs
        val threshold = maxOf(600.0, floor * 2.5)
        val voiced = amplitude >= threshold
        if (voiced) { speechMs += interval; lastSpeechMs = elapsedMs }
        else floor = (floor * .9 + amplitude.coerceAtMost(600) * .1).coerceIn(100.0, 600.0)
        result = when {
            elapsedMs >= 120_000 -> SpeechEndpoint.TIME_LIMIT
            speechMs >= 200 && lastSpeechMs?.let { elapsedMs - it >= 1200 } == true -> SpeechEndpoint.FINISHED
            elapsedMs >= 10_000 && speechMs < 200 -> SpeechEndpoint.NO_SPEECH
            else -> SpeechEndpoint.LISTENING
        }
        return result
    }
}
