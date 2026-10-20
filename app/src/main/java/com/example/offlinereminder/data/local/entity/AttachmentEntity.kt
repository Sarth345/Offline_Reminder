package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "event_attachments",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
