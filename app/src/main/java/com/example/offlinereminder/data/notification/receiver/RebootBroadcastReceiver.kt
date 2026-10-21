package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.notification.AndroidAlarmScheduler
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers valid future alarms on [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_MY_PACKAGE_REPLACED].
 * Delegates strictly to [RebootRecoveryUseCase] to ensure stale or cancelled alarms are never resurrected.
 */
class RebootBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = databaseOverride ?: try {
                    (context.applicationContext as? OfflineReminderApplication)?.container?.database
                        ?: AppDatabase.buildDatabase(context.applicationContext)
                } catch (_: Exception) {
                    AppDatabase.buildDatabase(context.applicationContext)
                }

                val timeProvider = SystemTimeProvider()
                val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
                val alarmScheduler = AndroidAlarmScheduler(context)
                val poster = NotificationPoster(context)

                val recoveryUseCase = RebootRecoveryUseCase(
                    notificationRepository = notificationRepo,
                    alarmScheduler = alarmScheduler,
                    timeProvider = timeProvider,
                    onMissedAlarm = { instance ->
                        poster.postMissedAlarmNotification(
                            eventId = instance.eventId,
                            eventTitle = "Missed Alarm",
                            notificationId = instance.requestCode
                        )
                    }
                )
                recoveryUseCase()
            } catch (ignored: Exception) {
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        @Volatile
        var databaseOverride: AppDatabase? = null
    }
}
