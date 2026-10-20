package com.example.offlinereminder.domain.notification

/**
 * Pure Kotlin abstraction over system alarm scheduling (e.g. Android AlarmManager).
 * Decouples domain scheduling logic from platform-specific APIs.
 */
interface AlarmScheduler {

    /**
     * Schedules a system wake-up alarm.
     *
     * @param requestCode Unique integer request code identifying the alarm pending intent.
     * @param triggerEpochMillis UTC timestamp in milliseconds when the alarm should trigger.
     * @param intentAction The broadcast intent action to fire.
     * @param extras Key-value payload passed with the intent.
     * @return True if the alarm was successfully registered with the system.
     */
    fun scheduleAlarm(
        requestCode: Int,
        triggerEpochMillis: Long,
        intentAction: String,
        extras: Map<String, String> = emptyMap()
    ): Boolean

    /**
     * Cancels an existing system alarm matching the [requestCode] and [intentAction].
     */
    fun cancelAlarm(
        requestCode: Int,
        intentAction: String
    )

    /**
     * Returns true if the system permits exact alarm scheduling.
     */
    fun canScheduleExactAlarms(): Boolean
}
