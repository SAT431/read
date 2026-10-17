package dev.folio.model.port

enum class AiProvider { GEMINI, GROQ }

/** Safe diagnostic metadata only; never include raw provider bodies, keys or request headers. */
class ProviderApiException(val provider: AiProvider, val httpStatus: Int, val retryAfterSeconds: Int? = null) : Exception(
    "${if (provider == AiProvider.GEMINI) "Gemini" else "Groq"}: " + when (httpStatus) {
        401, 403 -> "API authorization failed. Check the saved key and project access."
        404 -> "Requested model or endpoint is unavailable."
        429 -> "Quota or rate limit reached." + (retryAfterSeconds?.let { " Retry after $it seconds." } ?: " Try again later.")
        500, 502, 503, 504 -> "Provider temporarily unavailable (HTTP $httpStatus). Try again later."
        else -> "Request failed (HTTP $httpStatus)."
    })
