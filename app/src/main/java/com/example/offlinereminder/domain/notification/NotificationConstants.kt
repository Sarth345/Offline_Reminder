package com.example.offlinereminder.domain.notification

/**
 * Common constant identifiers for notification actions, extras, and channels.
 */
object NotificationConstants {
    const val ACTION_ALARM = "com.example.offlinereminder.ACTION_TRIGGER_NOTIFICATION"
    const val ACTION_COMPLETE = "com.example.offlinereminder.ACTION_NOTIFICATION_COMPLETE"
    const val ACTION_SNOOZE = "com.example.offlinereminder.ACTION_NOTIFICATION_SNOOZE"
    const val ACTION_DISMISS = "com.example.offlinereminder.ACTION_NOTIFICATION_DISMISS"

    const val EXTRA_INSTANCE_ID = "extra_instance_id"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_OCCURRENCE_START = "extra_occurrence_start"
    const val EXTRA_OCCURRENCE_ZONE = "extra_occurrence_zone"
    const val EXTRA_SNOOZE_DURATION_MILLIS = "extra_snooze_duration_millis"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_SILENT = "extra_is_silent"

    const val CHANNEL_ID_DEFAULT = "offline_reminders_default"
    const val CHANNEL_ID_SILENT = "offline_reminders_silent"

    const val DEFAULT_HORIZON_DAYS = 14L
}
