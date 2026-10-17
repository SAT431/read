package dev.folio.model.port

/** Credentials for our private service, never Gemini/Groq provider keys. */
class ServiceProfile(val baseUrl: String, val accessToken: String) {
    override fun toString() = "ServiceProfile(baseUrl=$baseUrl, accessToken=<redacted>)"
}
interface ServiceProfileStore {
    suspend fun load(): ServiceProfile?
    suspend fun save(profile: ServiceProfile)
    suspend fun clear()
}
