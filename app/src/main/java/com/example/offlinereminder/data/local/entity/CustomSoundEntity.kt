package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "custom_sounds")
data class CustomSoundEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,
    @ColumnInfo(name = "added_at")
    val addedAt: Instant
)
