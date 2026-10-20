package com.example.offlinereminder.domain.model

enum class RelationshipType {
    RELATED_TO,
    FOLLOW_UP_TO,
    PREPARATION_FOR,
    REPLACES
}

/**
 * Semantic association between two events without duplicating event definitions.
 */
data class EventRelationship(
    val sourceEventId: String,
    val targetEventId: String,
    val type: RelationshipType
) {
    init {
        require(sourceEventId != targetEventId) {
            "An event cannot have a relationship with itself (sourceEventId: $sourceEventId)"
        }
    }
}
