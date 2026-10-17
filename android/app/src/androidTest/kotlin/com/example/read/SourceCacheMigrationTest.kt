package com.example.read

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.folio.local.database.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceCacheMigrationTest {
    @Test fun migrationAddsPageCacheWithoutDiscardingLibraryOrWorkspace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "migration-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE workspace(id INTEGER NOT NULL PRIMARY KEY, revision INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE pdf_library(id TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, checksum TEXT NOT NULL, importedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_pdf_library_chapterId_checksum ON pdf_library(chapterId, checksum)")
            db.execSQL("INSERT INTO workspace VALUES(1, 7, 'preserved-workspace')")
            db.execSQL("INSERT INTO pdf_library VALUES('library','chapter','checksum',1,'preserved-library')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, FolioDatabase::class.java, file.absolutePath)
            .addMigrations(SourceCacheMigration).build()
        try {
            assertEquals("preserved-workspace", database.workspace().read()!!.payload)
            assertEquals("preserved-library", database.library().readAll().single().payload)
            assertTrue(database.pages().readAll().isEmpty())
        } finally { database.close(); file.delete() }
    }
}
