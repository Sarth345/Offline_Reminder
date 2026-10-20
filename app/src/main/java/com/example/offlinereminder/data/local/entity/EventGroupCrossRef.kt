package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "event_group_cross_ref",
    primaryKeys = ["event_id", "group_id"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["group_id"])
    ]
)
data class EventGroupCrossRef(
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "group_id")
    val groupId: String
)
