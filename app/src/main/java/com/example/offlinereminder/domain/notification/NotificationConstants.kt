package com.example.offlinereminder.domain.notification

/**
 * Common constant identifiers for notification actions, extras, and channels.
 */
object NotificationConstants {
    const val ACTION_ALARM = "com.example.offlinereminder.ACTION_TRIGGER_NOTIFICATION"
    const val ACTION_COMPLETE = "com.example.offlinereminder.ACTION_NOTIFICATION_COMPLETE"
    const val ACTION_SNOOZE = "com.example.offlinereminder.ACTION_NOTIFICATION_SNOOZE"
    const val ACTION_DISMISS = "com.example.offlinereminder.ACTION_NOTIFICATION_DISMISS"

    const val ACTION_START_ALARM = "com.example.offlinereminder.ACTION_START_ALARM"
    const val ACTION_STOP_ALARM = "com.example.offlinereminder.ACTION_STOP_ALARM"
    const val ACTION_DISMISS_ALARM = "com.example.offlinereminder.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.example.offlinereminder.ACTION_SNOOZE_ALARM"

    const val EXTRA_INSTANCE_ID = "extra_instance_id"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_OCCURRENCE_START = "extra_occurrence_start"
    const val EXTRA_OCCURRENCE_ZONE = "extra_occurrence_zone"
    const val EXTRA_SNOOZE_DURATION_MILLIS = "extra_snooze_duration_millis"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_SILENT = "extra_is_silent"

    const val EXTRA_EVENT_TITLE = "extra_event_title"
    const val EXTRA_EVENT_DESC = "extra_event_desc"
    const val EXTRA_SOUND_TYPE = "extra_sound_type"
    const val EXTRA_CUSTOM_SOUND_ID = "extra_custom_sound_id"
    const val EXTRA_SYSTEM_SOUND_URI = "extra_system_sound_uri"
    const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
    const val EXTRA_SNOOZE_DURATION_MINUTES = "extra_snooze_duration_minutes"

    const val CHANNEL_ID_DEFAULT = "offline_reminders_default"
    const val CHANNEL_ID_SILENT = "offline_reminders_silent"
    const val CHANNEL_ID_ALARM = "offline_reminders_alarm"

    const val ALARM_TIMEOUT_MILLIS = 10 * 60 * 1000L // 10 minutes

    const val DEFAULT_HORIZON_DAYS = 14L
}
