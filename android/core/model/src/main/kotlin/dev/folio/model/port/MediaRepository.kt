package dev.folio.model.port

/** Only registered source asset IDs may be loaded; features never choose filesystem paths. */
interface MediaRepository { suspend fun read(mediaId: String): ByteArray? }
