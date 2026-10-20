package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.offlinereminder.domain.model.RelationshipType

@Entity(
    tableName = "event_relationships",
    primaryKeys = ["source_event_id", "target_event_id"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["target_event_id"])
    ]
)
data class EventRelationshipEntity(
    @ColumnInfo(name = "source_event_id")
    val sourceEventId: String,
    @ColumnInfo(name = "target_event_id")
    val targetEventId: String,
    val type: RelationshipType
)
