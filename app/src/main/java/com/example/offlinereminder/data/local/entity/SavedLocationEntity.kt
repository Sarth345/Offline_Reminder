package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "saved_locations",
    indices = [
        Index(value = ["name"]),
        Index(value = ["is_favorite"])
    ]
)
data class SavedLocationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String = "",
    val building: String = "",
    val room: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
)
