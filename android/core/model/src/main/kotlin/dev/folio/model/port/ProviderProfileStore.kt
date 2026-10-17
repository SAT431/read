package dev.folio.model.port

/** User-owned provider credentials. Never expose these through AI context or UI summaries. */
class ProviderProfile(val geminiKey: String, val groqKey: String, val geminiModel: String) {
    init {
        require(geminiKey.length in 1..4000 && geminiKey.all { it.code in 33..126 })
        require(groqKey.isEmpty() || groqKey.length in 1..4000 && groqKey.all { it.code in 33..126 })
        require(geminiModel.matches(Regex("[A-Za-z0-9._-]{1,100}")))
    }
    override fun toString() = "ProviderProfile(keys=<redacted>, model=$geminiModel)"
}
interface ProviderProfileStore {
    suspend fun load(): ProviderProfile?
    suspend fun save(profile: ProviderProfile)
    suspend fun clear()
}
