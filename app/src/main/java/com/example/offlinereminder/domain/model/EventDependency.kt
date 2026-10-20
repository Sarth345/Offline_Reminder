package com.example.offlinereminder.domain.model

enum class DependencyType {
    BLOCKS,
    BLOCKED_BY,
    PREREQUISITE
}

/**
 * Directed task dependency relationship between two events.
 */
data class EventDependency(
    val sourceEventId: String,
    val targetEventId: String,
    val type: DependencyType = DependencyType.PREREQUISITE
) {
    init {
        require(sourceEventId != targetEventId) {
            "An event cannot depend on itself (sourceEventId: $sourceEventId)"
        }
    }
}
