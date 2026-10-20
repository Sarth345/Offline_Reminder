package com.example.offlinereminder.domain.notification

data class ScheduledAlarmRecord(
    val requestCode: Int,
    val triggerEpochMillis: Long,
    val intentAction: String,
    val extras: Map<String, String>
)

data class CancelledAlarmRecord(
    val requestCode: Int,
    val intentAction: String
)

/**
 * Pure Kotlin in-memory test double for [AlarmScheduler].
 * Captures all scheduling and cancellation requests for assertion in unit tests.
 */
class TestAlarmScheduler(
    var canScheduleExact: Boolean = true
) : AlarmScheduler {

    val scheduledAlarms = mutableListOf<ScheduledAlarmRecord>()
    val cancelledAlarms = mutableListOf<CancelledAlarmRecord>()

    override fun canScheduleExactAlarms(): Boolean = canScheduleExact

    override fun scheduleAlarm(
        requestCode: Int,
        triggerEpochMillis: Long,
        intentAction: String,
        extras: Map<String, String>
    ): Boolean {
        // If an alarm with the same requestCode already exists, replace it
        scheduledAlarms.removeAll { it.requestCode == requestCode }
        scheduledAlarms.add(
            ScheduledAlarmRecord(
                requestCode = requestCode,
                triggerEpochMillis = triggerEpochMillis,
                intentAction = intentAction,
                extras = extras
            )
        )
        return true
    }

    override fun cancelAlarm(
        requestCode: Int,
        intentAction: String
    ) {
        scheduledAlarms.removeAll { it.requestCode == requestCode }
        cancelledAlarms.add(
            CancelledAlarmRecord(
                requestCode = requestCode,
                intentAction = intentAction
            )
        )
    }

    fun clear() {
        scheduledAlarms.clear()
        cancelledAlarms.clear()
    }
}
