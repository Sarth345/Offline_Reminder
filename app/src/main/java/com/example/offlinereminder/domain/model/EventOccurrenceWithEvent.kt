package com.example.offlinereminder.domain.model

/**
 * Composite model binding an on-demand [EventOccurrence] with its parent [Event] definition.
 * Used for presentation and list views where occurrence timing and event metadata
 * (title, priority, categorization) must be rendered together.
 */
data class EventOccurrenceWithEvent(
    val occurrence: EventOccurrence,
    val event: Event
)
