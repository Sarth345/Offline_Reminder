package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "event_groups")
data class EventGroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val color: Int? = null,
    val icon: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
)
