package com.example.offlinereminder.domain.model

/**
 * Execution and completion state of an individual occurrence.
 */
enum class OccurrenceStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
    RESCHEDULED,
    OVERDUE,
    EXCLUDED
}
