package com.example.offlinereminder.domain.model

import java.time.LocalDate

/**
 * Immutable filter parameters for advanced event search and discovery.
 * Pure Kotlin, zero Android/Compose/Room dependencies.
 */
data class EventFilterParams(
    val query: String = "",
    val categoryIds: Set<String> = emptySet(),
    val eventTypeIds: Set<String> = emptySet(),
    val groupIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val priorities: Set<Priority> = emptySet(),
    val lifecycleStatuses: Set<EventLifecycleStatus> = setOf(EventLifecycleStatus.ACTIVE),
    val onlyFavorites: Boolean = false,
    val onlyOverdue: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    /**
     * Total number of active constraints applied (excluding default ACTIVE lifecycle).
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (categoryIds.isNotEmpty()) count += categoryIds.size
            if (eventTypeIds.isNotEmpty()) count += eventTypeIds.size
            if (groupIds.isNotEmpty()) count += groupIds.size
            if (tagIds.isNotEmpty()) count += tagIds.size
            if (priorities.isNotEmpty()) count += priorities.size
            // Count non-default lifecycle filter selections
            if (lifecycleStatuses != setOf(EventLifecycleStatus.ACTIVE) && lifecycleStatuses.isNotEmpty()) {
                count += lifecycleStatuses.size
            }
            if (onlyFavorites) count++
            if (onlyOverdue) count++
            if (startDate != null || endDate != null) count++
            return count
        }

    val hasActiveFilters: Boolean
        get() = activeFilterCount > 0
}
