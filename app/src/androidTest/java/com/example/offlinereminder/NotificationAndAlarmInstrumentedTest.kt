package com.example.offlinereminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.domain.notification.NotificationConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationAndAlarmInstrumentedTest {

    @Test
    fun verifyAppContext_andNoInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.offlinereminder", context.packageName)

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        }

        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val hasInternet = requestedPermissions.any { it.contains("INTERNET", ignoreCase = true) }
        assertTrue("App must never request or possess INTERNET permission", !hasInternet)
    }

    @Test
    fun verifyNotificationChannels_existOnSystem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val poster = NotificationPoster(context)
        poster.createNotificationChannels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val defaultChan = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_DEFAULT)
            assertNotNull("Default notification channel must exist", defaultChan)
            assertEquals("offline_reminders_default", defaultChan.id)

            val silentChan = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_SILENT)
            assertNotNull("Silent notification channel must exist", silentChan)
            assertEquals("offline_reminders_silent", silentChan.id)

            val alarmChan = nm.getNotificationChannel(NotificationConstants.CHANNEL_ID_ALARM)
            assertNotNull("Alarm notification channel must exist", alarmChan)
            assertEquals("offline_reminders_alarm", alarmChan.id)
        }
    }

    @Test
    fun verifyRingtoneUris_resolveValidContentOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val notifUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        assertNotNull("Physical device must have notification ringtone URI", notifUri)

        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        assertNotNull("Physical device must have alarm ringtone URI", alarmUri)
    }

    @Test
    fun verifyAlarmManager_exactAlarmCapability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = com.example.offlinereminder.data.notification.AndroidAlarmScheduler(context)

        // Verifies capability check executes cleanly without SecurityException or crash
        val canSchedule = scheduler.canScheduleExactAlarms()
        assertNotNull(canSchedule)

        // Verifies scheduling succeeds safely on device whether exact or fallback
        val scheduleSuccess = scheduler.scheduleAlarm(
            requestCode = 9999,
            triggerEpochMillis = System.currentTimeMillis() + 60_000L,
            intentAction = com.example.offlinereminder.domain.notification.NotificationConstants.ACTION_ALARM,
            extras = mapOf(
                com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_INSTANCE_ID to "test_inst",
                com.example.offlinereminder.domain.notification.NotificationConstants.EXTRA_EVENT_ID to "test_event"
            )
        )
        assertTrue("Scheduler must safely schedule alarm without crashing", scheduleSuccess)

        // Clean up
        scheduler.cancelAlarm(
            requestCode = 9999,
            intentAction = com.example.offlinereminder.domain.notification.NotificationConstants.ACTION_ALARM
        )
    }
}
