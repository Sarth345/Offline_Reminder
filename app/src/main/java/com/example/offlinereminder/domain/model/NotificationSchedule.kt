package com.example.offlinereminder.domain.model

import com.example.offlinereminder.core.result.Result
import java.time.Duration
import java.util.UUID

enum class NotificationTriggerType {
    BEFORE_START,
    AT_START,
    BEFORE_END,
    AT_END
}

/**
 * Definition of a notification schedule associated with an Event.
 * Describes the notification timing policy relative to event occurrences.
 * Android AlarmManager scheduling is handled strictly in the notification subsystem.
 */
data class NotificationSchedule(
    val id: String = UUID.randomUUID().toString(),
    val offsetDuration: Duration = Duration.ZERO,
    val triggerType: NotificationTriggerType = NotificationTriggerType.BEFORE_START,
    val profileId: String? = null
) {

    fun validate(): Result<Unit> {
        if (offsetDuration.isNegative) {
            return Result.Failure(IllegalArgumentException("Notification offsetDuration must not be negative"))
        }

        if ((triggerType == NotificationTriggerType.AT_START || triggerType == NotificationTriggerType.AT_END) &&
            offsetDuration != Duration.ZERO
        ) {
            return Result.Failure(
                IllegalArgumentException("Offset duration must be ZERO when trigger type is AT_START or AT_END")
            )
        }

        return Result.Success(Unit)
    }

    companion object {
        fun atStart(profileId: String? = null): NotificationSchedule =
            NotificationSchedule(
                offsetDuration = Duration.ZERO,
                triggerType = NotificationTriggerType.AT_START,
                profileId = profileId
            )

        fun beforeStart(offset: Duration, profileId: String? = null): NotificationSchedule =
            NotificationSchedule(
                offsetDuration = offset,
                triggerType = NotificationTriggerType.BEFORE_START,
                profileId = profileId
            )

        fun beforeEnd(offset: Duration, profileId: String? = null): NotificationSchedule =
            NotificationSchedule(
                offsetDuration = offset,
                triggerType = NotificationTriggerType.BEFORE_END,
                profileId = profileId
            )

        fun atEnd(profileId: String? = null): NotificationSchedule =
            NotificationSchedule(
                offsetDuration = Duration.ZERO,
                triggerType = NotificationTriggerType.AT_END,
                profileId = profileId
            )
    }
}
