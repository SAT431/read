package dev.folio.runtime

import dev.folio.runtime.assistant.*
import org.junit.Assert.*
import org.junit.Test

class EndOfSpeechDetectorTest {
    @Test fun speechEndsAfterQuietPauseWithoutAStopButton() {
        val detector = EndOfSpeechDetector()
        for (time in 100L..600L step 100) assertEquals(SpeechEndpoint.LISTENING, detector.observe(time, 5000))
        for (time in 700L..1700L step 100) assertEquals(SpeechEndpoint.LISTENING, detector.observe(time, 30))
        assertEquals(SpeechEndpoint.FINISHED, detector.observe(1800, 30))
        assertEquals(SpeechEndpoint.FINISHED, detector.observe(1900, 4000))
    }
    @Test fun shortWordPauseDoesNotSplitTheCommand() {
        val detector = EndOfSpeechDetector()
        for (time in 100L..400L step 100) detector.observe(time, 3000)
        for (time in 500L..1300L step 100) assertEquals(SpeechEndpoint.LISTENING, detector.observe(time, 100))
        detector.observe(1400, 5000)
        for (time in 1500L..2500L step 100) assertEquals(SpeechEndpoint.LISTENING, detector.observe(time, 100))
        assertEquals(SpeechEndpoint.FINISHED, detector.observe(2600, 100))
    }
    @Test fun silenceAndOneClickDoNotSendAudioForTranscription() {
        for (click in listOf(false, true)) {
            val detector = EndOfSpeechDetector()
            for (time in 100L..9900L step 100) detector.observe(time, if (click && time == 500L) 4000 else 50)
            assertEquals(SpeechEndpoint.NO_SPEECH, detector.observe(10000, 50))
        }
    }
    @Test fun continuousNoiseOrLongSpeechStillHasABoundedLimit() {
        val detector = EndOfSpeechDetector()
        for (time in 100L..119900L step 100) detector.observe(time, 10000)
        assertEquals(SpeechEndpoint.TIME_LIMIT, detector.observe(120000, 10000))
    }
}
