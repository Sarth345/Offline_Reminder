package com.example.offlinereminder.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.offlinereminder.data.notification.receiver.NotificationReceiver
import com.example.offlinereminder.domain.notification.AlarmScheduler

/**
 * Android platform implementation of [AlarmScheduler] using [AlarmManager].
 *
 * Implements strict capability checks for exact alarms (`canScheduleExactAlarms()`)
 * on Android 12+ (API 31+) and safely falls back to inexact/idle-friendly alarms
 * if permission is revoked or unavailable, preventing [SecurityException] crashes.
 */
class AndroidAlarmScheduler(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager: AlarmManager? by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun scheduleAlarm(
        requestCode: Int,
        triggerEpochMillis: Long,
        intentAction: String,
        extras: Map<String, String>
    ): Boolean {
        val manager = alarmManager ?: return false

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = intentAction
            extras.forEach { (key, value) -> putExtra(key, value) }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        return try {
            val isAlarm = extras[com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_DELIVERY_TYPE] == "ALARM" ||
                extras[com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_DELIVERY_TYPE] == com.example.offlinereminder.domain.model.ReminderDeliveryType.ALARM.name
            if (canScheduleExactAlarms()) {
                if (isAlarm) {
                    val showIntent = Intent(context, com.example.offlinereminder.MainActivity::class.java).apply {
                        this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_EVENT_ID, extras[com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_EVENT_ID])
                    }
                    val showPendingIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        showIntent,
                        flags
                    )
                    manager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerEpochMillis, showPendingIntent),
                        pendingIntent
                    )
                } else {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                }
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: SecurityException) {
            // Graceful fallback if exact alarm permission was revoked between check and invocation
            try {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
                true
            } catch (ignored: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun cancelAlarm(
        requestCode: Int,
        intentAction: String
    ) {
        val manager = alarmManager ?: return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = intentAction
        }

        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        if (pendingIntent != null) {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
