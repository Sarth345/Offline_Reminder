package com.example.offlinereminder.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity

data class EventGroupWithEvents(
    @Embedded
    val group: EventGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventGroupCrossRef::class,
            parentColumn = "group_id",
            entityColumn = "event_id"
        )
    )
    val events: List<EventEntity> = emptyList()
)
