package dev.folio.model.port

enum class ProviderFailureReason { TIMEOUT, NETWORK, TLS, RESPONSE_TOO_LARGE, MALFORMED_RESPONSE, INCOMPLETE_RESPONSE, BLOCKED_RESPONSE, IDENTITY_MISMATCH }

/** Only fixed diagnostics cross the UI boundary; never retain raw response/error text or causes. */
class ProviderFailureException(val provider: AiProvider, val reason: ProviderFailureReason) : Exception(
    "${if (provider == AiProvider.GEMINI) "Gemini" else "Groq"}: " + when (reason) {
        ProviderFailureReason.TIMEOUT -> "Request timed out. Your command was not applied. Try again when the connection or provider recovers."
        ProviderFailureReason.NETWORK -> "Could not connect. Check your network and try again."
        ProviderFailureReason.TLS -> "A secure connection could not be established. Check the device date and network."
        ProviderFailureReason.RESPONSE_TOO_LARGE -> "Response exceeded the supported size. Ask for a smaller task."
        ProviderFailureReason.MALFORMED_RESPONSE -> "The provider returned an invalid response. Try the request again."
        ProviderFailureReason.INCOMPLETE_RESPONSE -> "The provider did not finish its response. Try a smaller request."
        ProviderFailureReason.BLOCKED_RESPONSE -> "The provider could not return a response for this request. Try rephrasing it."
        ProviderFailureReason.IDENTITY_MISMATCH -> "The response did not match the current request. Your command was not applied; ask again."
    })
