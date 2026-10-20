package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupRepository
import com.example.offlinereminder.domain.backup.BackupValidator
import java.io.OutputStream

/**
 * Use case for creating, validating, and exporting local backup archives.
 */
class ExportBackupUseCase(
    private val backupRepository: BackupRepository,
    private val validator: BackupValidator = BackupValidator()
) {

    /**
     * Validates database state and writes a portable backup archive to [outputStream].
     */
    suspend operator fun invoke(outputStream: OutputStream): Result<BackupExportResult> = Result.runCatching {
        // 1. Verify that current state validates cleanly before generating output
        val payload = backupRepository.createBackupPayload().getOrThrow()
        validator.validate(payload).getOrThrow()

        // 2. Stream export
        backupRepository.exportBackupArchive(outputStream).getOrThrow()
    }

    /**
     * Creates and returns the in-memory [BackupPayload] for inspection or tests.
     */
    suspend fun createPayload(): Result<BackupPayload> = backupRepository.createBackupPayload()
}
