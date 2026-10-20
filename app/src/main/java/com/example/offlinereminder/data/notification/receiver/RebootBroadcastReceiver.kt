package com.example.offlinereminder.data.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.notification.AndroidAlarmScheduler
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers alarms on [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_MY_PACKAGE_REPLACED].
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
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).build()

                val timeProvider = SystemTimeProvider()
                val notificationRepo = OfflineNotificationRepository(db.notificationInstanceDao())
                val alarmScheduler = AndroidAlarmScheduler(context)

                val recoveryUseCase = RebootRecoveryUseCase(
                    notificationRepository = notificationRepo,
                    alarmScheduler = alarmScheduler,
                    timeProvider = timeProvider
                )
                recoveryUseCase()
            } catch (ignored: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
