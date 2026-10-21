package com.example.offlinereminder.data.sound

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only ContentProvider exposing validated application-owned custom audio files
 * to Android NotificationManager and system media components.
 *
 * Prevents arbitrary filesystem traversal and verifies metadata existence in Room
 * before opening file descriptors.
 */
class SoundContentProvider : ContentProvider() {

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val authority = context?.let { getAuthority(it) } ?: DEFAULT_AUTHORITY
        uriMatcher.addURI(authority, "$PATH_SOUNDS/*", MATCH_SOUND_BY_ID)
        uriMatcher.addURI(authority, "*", MATCH_SOUND_BY_ID_SHORT)
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw SecurityException("SoundContentProvider is read-only. Requested mode: $mode")
        }

        val match = uriMatcher.match(uri)
        if (match != MATCH_SOUND_BY_ID && match != MATCH_SOUND_BY_ID_SHORT) {
            throw FileNotFoundException("Unsupported URI pattern: $uri")
        }

        val customSoundId = uri.lastPathSegment
            ?: throw FileNotFoundException("Missing custom sound ID in URI: $uri")

        validateSoundId(customSoundId)

        val ctx = context ?: throw IllegalStateException("ContentProvider context is null")
        val db = getDatabase(ctx)

        val soundEntity = runBlocking(Dispatchers.IO) {
            db.customSoundDao().getById(customSoundId)
        } ?: throw FileNotFoundException("Custom sound metadata not found in database for ID: $customSoundId")

        val storage = CustomSoundStorage(ctx)
        val soundFile = storage.getSoundFile(soundEntity.fileName)

        if (!soundFile.exists() || !soundFile.isFile) {
            throw FileNotFoundException("Custom sound binary file does not exist: ${soundEntity.fileName}")
        }

        return ParcelFileDescriptor.open(soundFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val customSoundId = uri.lastPathSegment ?: return null
        validateSoundId(customSoundId)
        val ctx = context ?: return null
        val db = getDatabase(ctx)

        val entity = runBlocking(Dispatchers.IO) {
            db.customSoundDao().getById(customSoundId)
        } ?: return null

        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cursor.newRow()
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(col, entity.title.ifBlank { entity.fileName })
                OpenableColumns.SIZE -> row.add(col, entity.fileSizeBytes)
                else -> row.add(col, null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val customSoundId = uri.lastPathSegment ?: return null
        validateSoundId(customSoundId)
        val ctx = context ?: return null
        val db = getDatabase(ctx)
        val entity = runBlocking(Dispatchers.IO) {
            db.customSoundDao().getById(customSoundId)
        }
        return entity?.mimeType ?: "audio/*"
    }

    private fun validateSoundId(customSoundId: String) {
        val decodedId = try {
            java.net.URLDecoder.decode(customSoundId, "UTF-8")
        } catch (_: Exception) {
            customSoundId
        }
        val rawLower = customSoundId.lowercase()
        val decodedLower = decodedId.lowercase()

        if (rawLower.contains("..") || rawLower.contains("/") || rawLower.contains("\\") ||
            rawLower.contains("%2e") || rawLower.contains("%2f") || rawLower.contains("%5c") ||
            decodedLower.contains("..") || decodedLower.contains("/") || decodedLower.contains("\\")
        ) {
            throw SecurityException("Path traversal attempt in sound ID: $customSoundId")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("SoundContentProvider is read-only")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("SoundContentProvider is read-only")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("SoundContentProvider is read-only")
    }

    private fun getDatabase(ctx: Context): AppDatabase {
        return databaseOverride ?: try {
            (ctx.applicationContext as? OfflineReminderApplication)?.container?.database
                ?: AppDatabase.buildDatabase(ctx.applicationContext)
        } catch (_: Exception) {
            AppDatabase.buildDatabase(ctx.applicationContext)
        }
    }

    companion object {
        const val DEFAULT_AUTHORITY = "com.example.offlinereminder.sounds"
        const val AUTHORITY_SUFFIX = ".sounds"
        const val PATH_SOUNDS = "sounds"

        @Volatile
        var databaseOverride: AppDatabase? = null

        private const val MATCH_SOUND_BY_ID = 1
        private const val MATCH_SOUND_BY_ID_SHORT = 2

        fun getAuthority(context: Context): String = "${context.packageName}$AUTHORITY_SUFFIX"

        fun buildSoundUri(context: Context, customSoundId: String): Uri {
            return Uri.parse("content://${getAuthority(context)}/$PATH_SOUNDS/$customSoundId")
        }

        fun buildSoundUri(customSoundId: String): Uri {
            return Uri.parse("content://$DEFAULT_AUTHORITY/$PATH_SOUNDS/$customSoundId")
        }
    }
}
