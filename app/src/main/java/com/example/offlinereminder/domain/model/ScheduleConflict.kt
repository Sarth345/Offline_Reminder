package com.example.offlinereminder.domain.model

enum class ConflictType {
    DIRECT_OVERLAP,
    ALL_DAY_COEXISTENCE
}

enum class ConflictSeverity {
    HARD,
    ADVISORY
}

data class ScheduleConflict(
    val conflictingEventId: String,
    val conflictingEventTitle: String,
    val conflictingOccurrence: EventOccurrence,
    val existingEventId: String,
    val existingEventTitle: String,
    val existingOccurrence: EventOccurrence,
    val type: ConflictType,
    val severity: ConflictSeverity
)
