package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event

/**
 * Domain use case for validating an Event definition.
 * Enforces business constraints on required fields, recurrence rules, and notification offsets.
 */
class ValidateEventUseCase {

    operator fun invoke(event: Event): Result<Unit> {
        if (event.title.trim().isEmpty()) {
            return Result.Failure(IllegalArgumentException("Event title must not be blank"))
        }

        if (event.eventTypeId.trim().isEmpty()) {
            return Result.Failure(IllegalArgumentException("eventTypeId must not be blank"))
        }

        if (event.categoryId.trim().isEmpty()) {
            return Result.Failure(IllegalArgumentException("categoryId must not be blank"))
        }

        // Validate recurrence rule if present
        if (event.recurrenceRule != null) {
            val recurrenceValidation = event.recurrenceRule.validate()
            if (recurrenceValidation is Result.Failure) {
                return recurrenceValidation
            }
        }

        // Validate all notification schedules
        for (schedule in event.notificationSchedules) {
            val scheduleValidation = schedule.validate()
            if (scheduleValidation is Result.Failure) {
                return scheduleValidation
            }
        }

        // Validate checklist items if present
        if (event.checklist != null) {
            for (item in event.checklist.items) {
                if (item.text.trim().isEmpty()) {
                    return Result.Failure(IllegalArgumentException("Checklist item text must not be blank"))
                }
            }
        }

        return Result.Success(Unit)
    }
}
