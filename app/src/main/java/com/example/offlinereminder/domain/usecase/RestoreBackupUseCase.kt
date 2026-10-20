package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.domain.backup.BackupMigrator
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupRepository
import com.example.offlinereminder.domain.backup.BackupValidator
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.InputStream

/**
 * Use case to validate, migrate, and restore database state from a backup archive or payload.
 * Automatically reconciles and reschedules notification alarms after database mutations succeed.
 */
class RestoreBackupUseCase(
    private val backupRepository: BackupRepository,
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase,
    private val settingsRepository: SettingsRepository? = null,
    private val validator: BackupValidator = BackupValidator(),
    private val migrator: BackupMigrator = BackupMigrator()
) {

    /**
     * Restores state from an archive or raw JSON [inputStream] according to [strategy].
     */
    suspend operator fun invoke(
        inputStream: InputStream,
        strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ): Result<RestoreSummary> = Result.runCatching {
        val summary = backupRepository.importBackupArchive(inputStream, strategy).getOrThrow()

        // Post-restore notification reconciliation
        reconcileNotifications()

        summary
    }

    /**
     * Directly restores from a pre-parsed [BackupPayload] according to [strategy].
     */
    suspend fun restorePayload(
        payload: BackupPayload,
        strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ): Result<RestoreSummary> = Result.runCatching {
        // Validate before touching database
        validator.validate(payload).getOrThrow()

        val summary = backupRepository.restoreBackupPayload(payload, strategy).getOrThrow()

        // Post-restore notification reconciliation
        reconcileNotifications()

        summary
    }

    private suspend fun reconcileNotifications() {
        val activeEvents = eventRepository.getAllActiveEvents().first()
        val quietHours = settingsRepository?.getSettings()?.quietHours
        for (event in activeEvents) {
            if (event.notificationSchedules.isNotEmpty()) {
                val exceptions = occurrenceExceptionRepository.getExceptionsForEvent(event.id).first()
                scheduleNotificationsForEventUseCase(
                    event = event,
                    exceptions = exceptions,
                    quietHours = quietHours
                )
            }
        }
    }
}
