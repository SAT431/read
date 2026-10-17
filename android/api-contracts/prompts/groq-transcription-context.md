# Groq Whisper Large v3 transcription configuration — v1

Provider: Groq. Model: whisper-large-v3. Endpoint: POST /openai/v1/audio/transcriptions. This model transcribes audio; it does not orchestrate UI tools. Use transcription rather than translation to preserve the user's language. Do not force language=en when the user mixes Hindi and English.

Send a short, recorded speech segment after voice activity/end-of-utterance detection or push-to-talk completion. The server holds the API key. Text input remains available when transcription fails or quota is exhausted. Do not assume the batch transcription endpoint provides a continuous streaming connection.

Whisper's prompt is a brief spelling/context hint, not a system instruction. Build it from the current subject/chapter and a small source vocabulary, capped within the provider's 224-token prompt limit. Exclude private annotations that are unnecessary for transcription. Do not pack tool rules, UI command schemas or the whole chapter into the Whisper prompt.

Example context hint:
“NEET Biology study session. Current chapter: Cell: The Unit of Life. Vocabulary: ribosome, endoplasmic reticulum, Golgi apparatus, mitochondria, nucleus. Commands may include open chapter, next page, show PDF, pause quiz.”

Forward the resulting utterance to the Gemini orchestrator together with fresh app context. Treat the transcript as potentially imperfect: ambiguous chapter/page requests require resolution or clarification. Groq and Gemini quotas are handled separately; no automatic paid fallback is allowed.

Source: https://console.groq.com/docs/speech-to-text.
