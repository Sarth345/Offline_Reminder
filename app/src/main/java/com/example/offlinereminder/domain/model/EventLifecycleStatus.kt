package com.example.offlinereminder.domain.model

/**
 * Series / Entity-level lifecycle state of an Event definition.
 * Kept strictly independent from individual occurrence execution states.
 */
enum class EventLifecycleStatus {
    ACTIVE,
    ARCHIVED,
    TRASHED
}
