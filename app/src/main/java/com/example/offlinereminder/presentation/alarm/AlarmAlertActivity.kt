package com.example.offlinereminder.presentation.alarm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.offlinereminder.data.alarm.AlarmPlaybackService
import com.example.offlinereminder.data.alarm.AlarmState
import com.example.offlinereminder.data.alarm.AlarmStateManager
import com.example.offlinereminder.domain.notification.NotificationConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Visual-only full-screen presentation for active alarms.
 *
 * Designed to show above the lockscreen and turn the screen on.
 * Does NOT own or manage audio playback or vibration; if this Activity is closed,
 * backgrounded, or killed, playback continues uninterrupted in [AlarmPlaybackService].
 */
class AlarmAlertActivity : Activity() {

    private val activityScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var instanceId: String? = null
    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenVisibility()

        instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID)
        eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "Alarm Ringing"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(titleView)

        val snoozeButton = Button(this).apply {
            text = "Snooze"
            setOnClickListener {
                instanceId?.let { id ->
                    AlarmPlaybackService.snooze(this@AlarmAlertActivity, id, eventId)
                }
                finish()
            }
        }
        layout.addView(snoozeButton)

        val dismissButton = Button(this).apply {
            text = "Dismiss"
            setOnClickListener {
                instanceId?.let { id ->
                    AlarmPlaybackService.dismiss(this@AlarmAlertActivity, id, eventId)
                }
                finish()
            }
        }
        layout.addView(dismissButton)

        setContentView(layout)

        // Observe state transitions; if alarm is dismissed, snoozed, or timed out elsewhere, finish UI
        activityScope.launch {
            AlarmStateManager.getInstance().stateFlow.collect { snapshot ->
                if (snapshot.state != AlarmState.RINGING && snapshot.state != AlarmState.IDLE) {
                    finish()
                }
            }
        }
    }

    private fun configureLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
