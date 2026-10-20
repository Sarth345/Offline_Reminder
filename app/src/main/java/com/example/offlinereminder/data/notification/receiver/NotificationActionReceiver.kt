package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.notification.AndroidAlarmScheduler
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.data.repository.OccurrenceExceptionRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.OccurrenceMutationResult
import com.example.offlinereminder.domain.usecase.SnoozeNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Handles user interactions with notifications (Complete, Snooze, Dismiss).
 * Strictly mutates domain and persistence states without touching UI.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).build()

                val timeProvider = SystemTimeProvider()
                val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
                val alarmScheduler = AndroidAlarmScheduler(context)
                val poster = NotificationPoster(context)

                val notificationId = intent.getIntExtra(NotificationConstants.EXTRA_NOTIFICATION_ID, -1)
                val instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID)

                when (action) {
                    NotificationConstants.ACTION_COMPLETE -> {
                        val eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID)
                        val startStr = intent.getStringExtra(NotificationConstants.EXTRA_OCCURRENCE_START)
                        val zoneStr = intent.getStringExtra(NotificationConstants.EXTRA_OCCURRENCE_ZONE)

                        if (eventId != null && startStr != null && zoneStr != null) {
                            val ldt = LocalDateTime.parse(startStr)
                            val zone = ZoneId.of(zoneStr)

                            val eventAggregate = db.eventDao().getEventWithDetailsById(eventId)
                            if (eventAggregate != null) {
                                val completeUseCase = CompleteOccurrenceUseCase(timeProvider)
                                val result = completeUseCase(eventAggregate.toDomain(), ldt, zone)
                                if (result.isSuccess) {
                                    val mutation = (result as com.example.offlinereminder.core.result.Result.Success).data
                                    when (mutation) {
                                        is OccurrenceMutationResult.RecurringException -> {
                                            val exceptionRepo = OccurrenceExceptionRepositoryImpl(db.occurrenceExceptionDao())
                                            exceptionRepo.saveException(mutation.exception)
                                        }
                                        is OccurrenceMutationResult.OneTimeOccurrence -> {
                                            // Handled by event execution
                                        }
                                    }
                                }
                            }
                        }

                        if (instanceId != null) {
                            notificationRepo.updateStatus(instanceId, com.example.offlinereminder.domain.model.NotificationInstanceStatus.DELIVERED)
                        }
                        if (notificationId != -1) {
                            poster.cancelNotification(notificationId)
                        }
                    }

                    NotificationConstants.ACTION_SNOOZE -> {
                        val extraDuration = intent.getLongExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MILLIS, -1L)
                        val durationMillis = if (extraDuration > 0L) {
                            extraDuration
                        } else {
                            val defaultMinutes = try {
                                (context.applicationContext as? OfflineReminderApplication)?.container?.settingsRepository?.getSettings()?.defaultSnoozeDurationMinutes ?: 10
                            } catch (_: Exception) {
                                10
                            }
                            defaultMinutes * 60 * 1000L
                        }
                        if (instanceId != null) {
                            val snoozeUseCase = SnoozeNotificationUseCase(notificationRepo, alarmScheduler, timeProvider)
                            snoozeUseCase(instanceId, Duration.ofMillis(durationMillis))
                        }
                        if (notificationId != -1) {
                            poster.cancelNotification(notificationId)
                        }
                    }

                    NotificationConstants.ACTION_DISMISS -> {
                        if (instanceId != null) {
                            notificationRepo.updateStatus(instanceId, com.example.offlinereminder.domain.model.NotificationInstanceStatus.DISMISSED)
                        }
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
