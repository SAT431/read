package com.example.read

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.folio.local.security.KeystoreProviderProfileStore
import dev.folio.model.port.ProviderProfile
import dev.folio.model.port.GeminiModels
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KeystoreProfileTest {
    @Test fun credentialRecordIsEncryptedAndTamperingNeverLoadsAProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val store = KeystoreProviderProfileStore(context, "test-profile-$id", "$id.bin")
        val file = File(context.noBackupFilesDir, "$id.bin")
        val profile = ProviderProfile("test-gemini-secret-123456789", "test-groq-secret-123456789", "test-model")
        try {
            store.save(profile)
            val bytesOnDisk = file.readBytes().toString(Charsets.UTF_8)
            assertFalse(bytesOnDisk.contains(profile.geminiKey) || bytesOnDisk.contains(profile.groqKey))
            assertEquals(profile.geminiKey, store.load()!!.geminiKey)
            assertEquals(profile.groqKey, store.load()!!.groqKey)
            assertEquals(GeminiModels.CURRENT_FLASH, store.load()!!.geminiModel)
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertTrue(runCatching { store.load() }.isFailure)
        } finally {
            store.clear()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("test-profile-$id") }
        }
    }
    @Test fun modelUpgradePreservesBothEncryptedKeysAndDoesNotRewriteAgain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val alias = "test-model-upgrade-$id"
        val name = "$id.bin"
        val old = KeystoreProviderProfileStore(context, alias, name, "gemini-3.5-flash-lite")
        val current = KeystoreProviderProfileStore(context, alias, name)
        val profile = ProviderProfile("test-gemini-model-upgrade", "test-groq-model-upgrade", "gemini-3.5-flash-lite")
        val file = File(context.noBackupFilesDir, name)
        try {
            old.save(profile)
            val before = file.readBytes()
            val upgraded = current.load()!!
            assertEquals(profile.geminiKey, upgraded.geminiKey)
            assertEquals(profile.groqKey, upgraded.groqKey)
            assertEquals(GeminiModels.CURRENT_FLASH, upgraded.geminiModel)
            val after = file.readBytes()
            assertFalse(before.contentEquals(after))
            current.load()
            assertArrayEquals(after, file.readBytes())
        } finally {
            current.clear()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
