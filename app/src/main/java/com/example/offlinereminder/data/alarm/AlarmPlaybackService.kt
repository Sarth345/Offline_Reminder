package com.example.offlinereminder.data.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.notification.AndroidAlarmScheduler
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.usecase.DismissNotificationUseCase
import com.example.offlinereminder.domain.usecase.SnoozeNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Foreground service that owns and maintains continuous ALARM-mode audio playback
 * and user-facing notifications.
 *
 * Runs with `foregroundServiceType="mediaPlayback"`.
 * Autonomous from any Activity: playback continues uninterrupted even if [AlarmAlertActivity]
 * is not launched, backgrounded, or destroyed.
 */
class AlarmPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var timeoutJob: Job? = null

    private lateinit var ringtonePlayer: AlarmRingtonePlayer
    private lateinit var poster: NotificationPoster
    private val stateManager: AlarmStateManager by lazy { AlarmStateManager.getInstance() }

    private var vibrator: Vibrator? = null
    private var currentInstanceId: String? = null
    private var currentEventId: String? = null
    private var currentEventTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        ringtonePlayer = AlarmRingtonePlayer(this)
        poster = NotificationPoster(this)
        vibrator = getSystemService(Vibrator::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action
        val instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID)
        val eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID)

        Log.d("PlanerizeAlarm", "onStartCommand: action=$action, instanceId=$instanceId, eventId=$eventId")

        when (action) {
            NotificationConstants.ACTION_START_ALARM -> {
                if (instanceId.isNullOrBlank()) {
                    Log.w("PlanerizeAlarm", "ACTION_START_ALARM received with blank instanceId. Stopping service.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                handleStartAlarm(intent, instanceId, eventId)
            }
            NotificationConstants.ACTION_DISMISS_ALARM -> {
                handleDismissAlarm(instanceId)
            }
            NotificationConstants.ACTION_SNOOZE_ALARM -> {
                val durationMillis = intent.getLongExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MILLIS, 15 * 60 * 1000L)
                handleSnoozeAlarm(instanceId, durationMillis)
            }
            NotificationConstants.ACTION_STOP_ALARM -> {
                handleStopAlarm()
            }
            else -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartAlarm(intent: Intent, instanceId: String, eventId: String?) {
        if (currentInstanceId == instanceId && stateManager.currentState() == AlarmState.RINGING) {
            Log.d("PlanerizeAlarm", "Instance '$instanceId' is already actively ringing. Ignoring duplicate start.")
            return
        }

        val db = getDatabase()
        val instance = try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                db.notificationInstanceDao().getById(instanceId)
            }
        } catch (e: Exception) {
            Log.w("PlanerizeAlarm", "Database status check failed for instance '$instanceId'", e)
            null
        }
        val terminalStatuses = setOf("DELIVERED", "DISMISSED", "CANCELLED", "TIMED_OUT", "COMPLETED")
        if (instance != null && terminalStatuses.contains(instance.status)) {
            Log.d("PlanerizeAlarm", "Instance '$instanceId' is in terminal status '${instance.status}'. Aborting alarm start.")
            stopSelf()
            return
        }

        currentInstanceId = instanceId
        currentEventId = eventId
        val eventTitle = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_TITLE) ?: "Alarm"
        val eventDesc = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_DESC) ?: ""
        val soundTypeStr = intent.getStringExtra(NotificationConstants.EXTRA_SOUND_TYPE)
        val customSoundId = intent.getStringExtra(NotificationConstants.EXTRA_CUSTOM_SOUND_ID)
        val systemUriString = intent.getStringExtra(NotificationConstants.EXTRA_SYSTEM_SOUND_URI)
        val builtInSoundKey = intent.getStringExtra(NotificationConstants.EXTRA_BUILT_IN_SOUND_KEY)
        val vibrationEnabled = intent.getBooleanExtra(NotificationConstants.EXTRA_VIBRATION_ENABLED, true)
        val snoozeDurationMinutes = intent.getIntExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MINUTES, 15)
        currentEventTitle = eventTitle

        Log.d("PlanerizeAlarm", "handleStartAlarm for '$eventTitle' (id: $instanceId, soundType: $soundTypeStr, snooze: ${snoozeDurationMinutes}m)")

        // 1. Post foreground notification immediately
        val foregroundNotification = poster.buildAlarmForegroundNotification(
            instanceId = instanceId,
            eventId = eventId ?: "",
            eventTitle = eventTitle,
            eventDesc = eventDesc,
            snoozeDurationMinutes = snoozeDurationMinutes
        )

        val notificationId = instanceId.hashCode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, foregroundNotification)
        }

        // 2. Transition state to RINGING in memory and ensure Room status is RINGING
        stateManager.transitionTo(instanceId, eventId, AlarmState.RINGING)
        serviceScope.launch(Dispatchers.IO) {
            try {
                db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.RINGING.name)
            } catch (e: Exception) {
                Log.w("PlanerizeAlarm", "Database status update failed for instance '$instanceId'", e)
            }
        }

        // 4. Resolve and play audio via RingtonePlayer
        val soundType = try {
            if (soundTypeStr != null) SoundSourceType.valueOf(soundTypeStr) else SoundSourceType.DEFAULT
        } catch (_: Exception) {
            SoundSourceType.DEFAULT
        }
        val soundRef = SoundReference(
            type = soundType,
            customSoundId = customSoundId,
            systemUriString = systemUriString,
            builtInSoundKey = builtInSoundKey
        )
        ringtonePlayer.play(soundRef, isLooping = true)

        // 5. Start vibration if enabled
        if (vibrationEnabled) {
            startVibrating()
        }

        // 6. Schedule deterministic 10-minute timeout
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(NotificationConstants.ALARM_TIMEOUT_MILLIS)
            handleTimeout(instanceId, eventId, eventTitle, notificationId)
        }
    }

    private fun handleDismissAlarm(instanceId: String?) {
        val targetId = instanceId ?: currentInstanceId
        stopPlaybackAndVibration()

        if (targetId != null) {
            stateManager.transitionTo(targetId, currentEventId, AlarmState.DISMISSED)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val db = getDatabase()
                    val repo = OfflineNotificationRepository(db.notificationInstanceDao())
                    val scheduler = AndroidAlarmScheduler(applicationContext)
                    val dismissUseCase = DismissNotificationUseCase(repo, scheduler)
                    dismissUseCase(targetId)
                    // Explicitly ensure status is DISMISSED in Room
                    db.notificationInstanceDao().updateStatus(targetId, NotificationInstanceStatus.DISMISSED.name)
                } catch (_: Exception) {
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSnoozeAlarm(instanceId: String?, durationMillis: Long) {
        val targetId = instanceId ?: currentInstanceId
        stopPlaybackAndVibration()

        if (targetId != null) {
            stateManager.transitionTo(targetId, currentEventId, AlarmState.SNOOZED)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val db = getDatabase()
                    val repo = OfflineNotificationRepository(db.notificationInstanceDao())
                    val scheduler = AndroidAlarmScheduler(applicationContext)
                    val timeProvider = SystemTimeProvider()
                    val snoozeUseCase = SnoozeNotificationUseCase(repo, scheduler, timeProvider)
                    snoozeUseCase(targetId, Duration.ofMillis(durationMillis))
                } catch (_: Exception) {
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleStopAlarm() {
        stopPlaybackAndVibration()
        currentInstanceId?.let {
            stateManager.transitionTo(it, currentEventId, AlarmState.IDLE)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleTimeout(
        instanceId: String,
        eventId: String?,
        eventTitle: String,
        notificationId: Int
    ) {
        if (stateManager.currentState() != AlarmState.RINGING) {
            return
        }

        stopPlaybackAndVibration()
        stateManager.transitionTo(instanceId, eventId, AlarmState.TIMED_OUT)

        // Room status updated to TIMED_OUT (the alarm was ringing and timed out after 10 minutes)
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = getDatabase()
                db.notificationInstanceDao().updateStatus(instanceId, NotificationInstanceStatus.TIMED_OUT.name)
            } catch (_: Exception) {
            }
        }

        // Show missed alarm tray notification
        poster.postMissedAlarmNotification(eventId ?: "", eventTitle, notificationId)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startVibrating() {
        try {
            val pattern = longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopPlaybackAndVibration() {
        timeoutJob?.cancel()
        timeoutJob = null
        try {
            ringtonePlayer.stop()
        } catch (_: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun getDatabase(): AppDatabase {
        return databaseOverride ?: try {
            (applicationContext as? OfflineReminderApplication)?.container?.database
                ?: AppDatabase.buildDatabase(applicationContext)
        } catch (_: Exception) {
            AppDatabase.buildDatabase(applicationContext)
        }
    }

    override fun onDestroy() {
        stopPlaybackAndVibration()
        try {
            ringtonePlayer.release()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var databaseOverride: AppDatabase? = null

        @Volatile
        var startForegroundServiceOverride: ((Context, Intent) -> Unit)? = null

        fun start(
            context: Context,
            instanceId: String,
            eventId: String,
            eventTitle: String,
            eventDesc: String,
            sound: SoundReference,
            vibrationEnabled: Boolean,
            snoozeDurationMinutes: Int
        ) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = NotificationConstants.ACTION_START_ALARM
                putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
                putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
                putExtra(NotificationConstants.EXTRA_EVENT_TITLE, eventTitle)
                putExtra(NotificationConstants.EXTRA_EVENT_DESC, eventDesc)
                putExtra(NotificationConstants.EXTRA_SOUND_TYPE, sound.type.name)
                putExtra(NotificationConstants.EXTRA_CUSTOM_SOUND_ID, sound.customSoundId)
                putExtra(NotificationConstants.EXTRA_SYSTEM_SOUND_URI, sound.systemUriString)
                putExtra(NotificationConstants.EXTRA_BUILT_IN_SOUND_KEY, sound.builtInSoundKey)
                putExtra(NotificationConstants.EXTRA_VIBRATION_ENABLED, vibrationEnabled)
                putExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MINUTES, snoozeDurationMinutes)
            }
            val override = startForegroundServiceOverride
            if (override != null) {
                override(context, intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = NotificationConstants.ACTION_STOP_ALARM
            }
            context.startService(intent)
        }

        fun dismiss(context: Context, instanceId: String, eventId: String? = null) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = NotificationConstants.ACTION_DISMISS_ALARM
                putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
                putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
            }
            context.startService(intent)
        }

        fun snooze(context: Context, instanceId: String, eventId: String? = null, durationMillis: Long = 15 * 60 * 1000L) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                action = NotificationConstants.ACTION_SNOOZE_ALARM
                putExtra(NotificationConstants.EXTRA_INSTANCE_ID, instanceId)
                putExtra(NotificationConstants.EXTRA_EVENT_ID, eventId)
                putExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MILLIS, durationMillis)
            }
            context.startService(intent)
        }
    }
}
