package com.example.offlinereminder.domain.backup

import com.example.offlinereminder.core.result.Result
import java.io.InputStream
import java.io.OutputStream

/**
 * Domain abstraction for backup payload export, import, and archive packaging.
 */
interface BackupRepository {

    /**
     * Extracts full database state and packages it into a canonical [BackupPayload].
     */
    suspend fun createBackupPayload(): Result<BackupPayload>

    /**
     * Atomically restores database state from a validated [BackupPayload] according to [strategy].
     */
    suspend fun restoreBackupPayload(
        payload: BackupPayload,
        strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ): Result<RestoreSummary>

    /**
     * Exports a portable backup archive (containing backup.json and local attachments) to [outputStream].
     */
    suspend fun exportBackupArchive(
        outputStream: OutputStream
    ): Result<BackupExportResult>

    /**
     * Restores state from an archive or JSON [inputStream] according to [strategy].
     */
    suspend fun importBackupArchive(
        inputStream: InputStream,
        strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ): Result<RestoreSummary>
}
