package dev.folio.model.port

object GeminiModels {
    const val CURRENT_FLASH = "gemini-3.8-flash"
    const val DISPLAY_NAME = "Gemini 3.8 Flash"
    private val retiredFlashModels = setOf(
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-preview-05-20",
        "gemini-3.0-flash",
        "gemini-3-flash"
    )
    fun normalize(model: String): String =
        model.trim().let { if (it in retiredFlashModels || it.isBlank()) CURRENT_FLASH else it }
}
